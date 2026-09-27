package com.raktsetu.server;

import com.raktsetu.domain.exception.InsufficientStockException;
import com.raktsetu.domain.exception.RaktSetuException;
import com.raktsetu.domain.model.BloodComponent;
import com.raktsetu.domain.model.BloodGroup;
import com.raktsetu.domain.model.BloodRequest;
import com.raktsetu.domain.model.ComponentType;
import com.raktsetu.domain.model.Facility;
import com.raktsetu.domain.model.Route;
import com.raktsetu.domain.model.TransferOrder;
import com.raktsetu.domain.model.Urgency;
import com.raktsetu.engine.Allocation;
import com.raktsetu.engine.BloodNetwork;
import com.raktsetu.engine.Freshness;
import com.raktsetu.engine.RequestOutcome;
import com.raktsetu.server.ApiModels.BloodRequestInput;
import com.raktsetu.server.ApiModels.NotificationList;
import com.raktsetu.server.ApiModels.NotificationView;
import com.raktsetu.server.ApiModels.RequestPart;
import com.raktsetu.server.ApiModels.RequestView;
import com.raktsetu.server.ApiModels.Summary;
import com.raktsetu.server.ApiModels.TransferView;
import com.raktsetu.server.db.NotificationRepository;
import com.raktsetu.server.db.NotificationRepository.Kind;
import com.raktsetu.server.db.RequestRepository;
import com.raktsetu.server.db.StateRepository;
import com.raktsetu.server.db.TransferRepository;
import com.raktsetu.server.security.AppUser;
import com.raktsetu.server.security.Role;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The day-to-day workflows between people: hospitals request blood and confirm receipt, centres dispatch it, the
 * network administrator plans and approves transfers, and everyone gets notifications about what needs their
 * attention. Every change goes through {@link NetworkService#change} so stock, the audit log and the workflow tables
 * are written in one transaction; all methods lock the same object as {@link NetworkService}.
 */
@Service
public class WorkflowService {

    private static final int LIST_LIMIT = 50;

    private final NetworkService network;
    private final RequestRepository requests;
    private final TransferRepository transfers;
    private final NotificationRepository notifications;
    private final StateRepository state;
    private final TransactionTemplate tx;

    public WorkflowService(NetworkService network, RequestRepository requests, TransferRepository transfers,
                           NotificationRepository notifications, StateRepository state, TransactionTemplate tx) {
        this.network = network;
        this.requests = requests;
        this.transfers = transfers;
        this.notifications = notifications;
        this.state = state;
        this.tx = tx;
        synchronized (network) {
            dailyDigest();
        }
    }

    // ------------------------------------------------------------------ requests

    /**
     * Raises a request and reserves units for it straight away (taking them off the shelf), then tells each supplying
     * centre what to dispatch. A request the network cannot meet is recorded as UNMET and administrators are told.
     */
    public RequestView raise(BloodRequestInput input, AppUser user) throws RaktSetuException {
        if (input == null || input.hospitalId() == null || input.hospitalId().isBlank()) {
            throw new IllegalArgumentException("hospitalId is required");
        }
        if (input.quantity() == null || input.quantity() < 1 || input.quantity() > 50) {
            throw new IllegalArgumentException("quantity must be between 1 and 50");
        }
        BloodRequest request = new BloodRequest(input.hospitalId().trim(), ComponentType.parse(input.component()),
                BloodGroup.parse(input.group()), input.quantity(),
                input.urgency() == null || input.urgency().isBlank() ? Urgency.EMERGENCY
                        : Urgency.parse(input.urgency()));
        synchronized (network) {
            LocalDate today = network.today();
            network.network().facility(request.hospitalId());
            long[] id = new long[1];
            try {
                network.change(() -> network.requests().handle(request, today), outcome -> {
                    id[0] = requests.insert(request, RequestRepository.Status.ALLOCATED, today, user.username(),
                            substitutionNote(outcome));
                    for (Allocation a : outcome.allocations()) {
                        for (BloodComponent u : a.units()) {
                            requests.addUnit(id[0], a.source().getId(), u);
                        }
                    }
                    for (Allocation a : outcome.allocations()) {
                        notifications.add(today, a.source().getId(), Kind.REQUEST_TO_DISPATCH,
                                "Request #" + id[0] + " from " + request.hospitalId() + ": dispatch "
                                        + a.units().size() + " x " + a.donorGroup() + " " + request.component() + " ("
                                        + a.units().stream().map(BloodComponent::getId)
                                        .collect(Collectors.joining(", ")) + ")");
                    }
                });
            } catch (InsufficientStockException e) {
                tx.executeWithoutResult(s -> {
                    long unmet = requests.insert(request, RequestRepository.Status.UNMET, today, user.username(),
                            e.getMessage());
                    notifications.add(today, null, Kind.REQUEST_UNMET, "Request #" + unmet + " from "
                            + request.hospitalId() + " could not be met. " + e.getMessage());
                });
                throw e;
            }
            return view(id[0]);
        }
    }

    public List<RequestView> requests(AppUser user) {
        synchronized (network) {
            List<RequestRepository.Row> rows = switch (user.role()) {
                case ADMIN -> requests.latest(null, null, LIST_LIMIT);
                case HOSPITAL_STAFF -> requests.latest(user.facilityId(), null, LIST_LIMIT);
                case CENTRE_STAFF -> requests.latest(null, user.facilityId(), LIST_LIMIT);
            };
            return rows.stream().map(r -> view(r, requests.units(r.id()))).toList();
        }
    }

    /**
     * Marks a centre's part of a request as sent. Centre staff dispatch their own units; an administrator dispatches
     * every part. When every part is on its way the hospital is told.
     */
    public RequestView dispatch(long id, AppUser user) throws RaktSetuException {
        synchronized (network) {
            RequestRepository.Row row = request(id);
            requireStatus(row, RequestRepository.Status.ALLOCATED);
            List<RequestRepository.UnitRow> units = requests.units(id);
            List<String> sources = units.stream().map(RequestRepository.UnitRow::sourceId).distinct().toList();
            if (!user.isAdmin() && (user.role() != Role.CENTRE_STAFF || !sources.contains(user.facilityId()))) {
                throw new AccessDeniedException("Only a centre supplying request #" + id + " can dispatch it");
            }
            LocalDate today = network.today();
            tx.executeWithoutResult(s -> {
                for (String source : user.isAdmin() ? sources : List.of(user.facilityId())) {
                    requests.markDispatched(id, source);
                }
                if (requests.units(id).stream().allMatch(RequestRepository.UnitRow::dispatched)) {
                    requests.setStatus(id, RequestRepository.Status.DISPATCHED, today);
                    notifications.add(today, row.hospitalId(), Kind.REQUEST_DISPATCHED, "Request #" + id + " ("
                            + row.quantity() + " x " + row.group() + " " + row.component() + ") is on its way from "
                            + String.join(", ", sources) + ". Confirm when it arrives.");
                }
            });
            return view(id);
        }
    }

    public RequestView receive(long id, AppUser user) throws RaktSetuException {
        synchronized (network) {
            RequestRepository.Row row = request(id);
            user.requireActingFor(row.hospitalId(), Role.HOSPITAL_STAFF);
            requireStatus(row, RequestRepository.Status.DISPATCHED);
            requests.setStatus(id, RequestRepository.Status.RECEIVED, network.today());
            return view(id);
        }
    }

    /** Cancels a request that has not been dispatched: its units go back on their shelves. */
    public RequestView cancel(long id, AppUser user) throws RaktSetuException {
        synchronized (network) {
            RequestRepository.Row row = request(id);
            user.requireActingFor(row.hospitalId(), Role.HOSPITAL_STAFF);
            requireStatus(row, RequestRepository.Status.ALLOCATED);
            List<RequestRepository.UnitRow> units = requests.units(id);
            if (units.stream().anyMatch(RequestRepository.UnitRow::dispatched)) {
                throw new RaktSetuException("Request #" + id + " is already partly dispatched and cannot be cancelled");
            }
            Map<String, List<BloodComponent>> bySource = new LinkedHashMap<>();
            for (RequestRepository.UnitRow u : units) {
                bySource.computeIfAbsent(u.sourceId(), k -> new ArrayList<>()).add(u.toUnit());
            }
            LocalDate today = network.today();
            network.change(() -> {
                for (Map.Entry<String, List<BloodComponent>> e : bySource.entrySet()) {
                    network.network().returnUnits(e.getKey(), e.getValue(), "request #" + id + " cancelled", today);
                }
                return bySource;
            }, done -> {
                requests.setStatus(id, RequestRepository.Status.CANCELLED, today);
                for (Map.Entry<String, List<BloodComponent>> e : done.entrySet()) {
                    notifications.add(today, e.getKey(), Kind.REQUEST_CANCELLED, "Request #" + id + " was cancelled; "
                            + e.getValue().size() + " unit(s) are back on your shelf.");
                }
            });
            return view(id);
        }
    }

    // ------------------------------------------------------------------ transfers

    /** Replaces any still-proposed transfers with a new plan from the named strategy. */
    public List<TransferView> plan(String strategy) {
        if (strategy == null || strategy.isBlank()) {
            throw new IllegalArgumentException("strategy is required");
        }
        synchronized (network) {
            List<TransferOrder> orders = network.engine().plan(strategy, network.network(), network.today());
            tx.executeWithoutResult(s -> {
                transfers.supersedeProposed();
                orders.forEach(transfers::insert);
            });
            return transfers();
        }
    }

    public List<TransferView> transfers() {
        synchronized (network) {
            return transfers.current(LIST_LIMIT).stream().map(WorkflowService::view).toList();
        }
    }

    /** Approves a proposed transfer: the stock moves, and both centres are told. */
    public TransferView approve(String id, AppUser user) throws RaktSetuException {
        synchronized (network) {
            TransferRepository.Row row = transfer(id);
            if (row.status() != TransferRepository.Status.PROPOSED) {
                throw new RaktSetuException("Transfer " + id + " is already " + row.status());
            }
            BloodNetwork net = network.network();
            Facility source = net.facility(row.sourceId());
            Facility destination = net.facility(row.destinationId());
            List<BloodComponent> units = new ArrayList<>();
            for (String unitId : transfers.unitIds(id)) {
                source.getInventory().find(unitId).ifPresent(units::add);
            }
            if (units.size() != row.unitCount()) {
                throw new RaktSetuException("Stock at " + row.sourceId() + " has changed since transfer " + id
                        + " was planned; generate a new plan");
            }
            TransferOrder order = new TransferOrder(id, source, destination, units, row.plannedOn(),
                    new Route(row.distanceKm(), row.travelMinutes()), row.reason());
            LocalDate today = network.today();
            network.change(() -> {
                network.transfers().approve(order, today);
                return order;
            }, o -> {
                transfers.decide(id, TransferRepository.Status.APPROVED, user.username(), today);
                notifications.add(today, row.sourceId(), Kind.TRANSFER_OUTGOING, "Send transfer " + id + ": "
                        + row.units() + " to " + row.destinationId() + " (" + row.distanceKm() + " km).");
                notifications.add(today, row.destinationId(), Kind.TRANSFER_INCOMING, "Incoming transfer " + id
                        + ": " + row.units() + " from " + row.sourceId() + ". Confirm when it arrives.");
            });
            return view(transfer(id));
        }
    }

    public TransferView reject(String id, AppUser user) throws RaktSetuException {
        synchronized (network) {
            TransferRepository.Row row = transfer(id);
            if (row.status() != TransferRepository.Status.PROPOSED) {
                throw new RaktSetuException("Transfer " + id + " is already " + row.status());
            }
            transfers.decide(id, TransferRepository.Status.REJECTED, user.username(), network.today());
            return view(transfer(id));
        }
    }

    /** The destination confirms an approved transfer arrived. */
    public TransferView receiveTransfer(String id, AppUser user) throws RaktSetuException {
        synchronized (network) {
            TransferRepository.Row row = transfer(id);
            user.requireActingFor(row.destinationId(), Role.CENTRE_STAFF);
            if (row.status() != TransferRepository.Status.APPROVED) {
                throw new RaktSetuException("Transfer " + id + " is " + row.status() + ", not on its way");
            }
            transfers.markReceived(id, network.today());
            return view(transfer(id));
        }
    }

    // ------------------------------------------------------------------ centres

    /** Removes a damaged unit from a centre's stock, recording why. */
    public void discard(String facilityId, String unitId, String reason) throws RaktSetuException {
        synchronized (network) {
            LocalDate today = network.today();
            network.change(() -> network.network().discardUnit(facilityId, unitId, reason, today));
        }
    }

    // ------------------------------------------------------------------ date and notifications

    /** Moves the demo date forward, then sends the new day's reminders. */
    public Summary advanceTo(LocalDate date) throws RaktSetuException {
        synchronized (network) {
            network.advanceTo(date);
            dailyDigest();
            return network.summary();
        }
    }

    public NotificationList notifications(AppUser user) {
        String audience = user.isAdmin() ? null : user.facilityId();
        List<NotificationView> items = notifications.latest(audience, 30).stream()
                .map(n -> new NotificationView(n.id(), n.createdOn(), n.kind(), n.message(), n.read())).toList();
        return new NotificationList(notifications.unread(audience), items);
    }

    public void markRead(long id, AppUser user) {
        if (!notifications.markRead(id, user.isAdmin() ? null : user.facilityId())) {
            throw new IllegalArgumentException("No notification " + id + " for you");
        }
    }

    public void markAllRead(AppUser user) {
        notifications.markAllRead(user.isAdmin() ? null : user.facilityId());
    }

    /**
     * Once per day: tells each centre about stock expiring within two days and overdue stock reports, and tells
     * administrators which centres' data cannot be trusted and how much stock is at risk.
     */
    private void dailyDigest() {
        LocalDate today = network.today();
        if (state.get(StateRepository.LAST_DIGEST).map(today.toString()::equals).orElse(false)) {
            return;
        }
        BloodNetwork net = network.network();
        List<String> unverified = new ArrayList<>();
        int atRisk = network.engine().risks(net, today).stream().mapToInt(r -> r.atRisk().size()).sum();
        tx.executeWithoutResult(s -> {
            for (Facility f : net.storingFacilities()) {
                int urgent = network.urgentUnitsAt(f);
                if (urgent > 0) {
                    notifications.add(today, f.getId(), Kind.EXPIRING_SOON, urgent + " unit(s) expire within 2 days. "
                            + "Issue them first, or they may be offered to other centres.");
                }
                if (Freshness.of(f, today) == Freshness.UNVERIFIED) {
                    unverified.add(f.getId());
                    notifications.add(today, f.getId(), Kind.REPORT_OVERDUE, "Your stock was last reported on "
                            + (f.getLastReportedOn() == null ? "never" : f.getLastReportedOn())
                            + ". Please report today's stock so the network can rely on it.");
                }
            }
            if (!unverified.isEmpty()) {
                notifications.add(today, null, Kind.REPORT_OVERDUE, "Stock data is unverified (over 3 days old) for "
                        + String.join(", ", unverified) + ". Their stock is searched last and receives no transfers.");
            }
            if (atRisk > 0) {
                notifications.add(today, null, Kind.EXPIRING_SOON, atRisk + " unit(s) are at risk of expiring unused "
                        + "this week. Generate a transfer plan to rescue them.");
            }
            state.put(StateRepository.LAST_DIGEST, today.toString());
        });
    }

    // ------------------------------------------------------------------ helpers

    private RequestRepository.Row request(long id) throws RaktSetuException {
        return requests.find(id).orElseThrow(() -> new RaktSetuException("No request #" + id));
    }

    private TransferRepository.Row transfer(String id) throws RaktSetuException {
        return transfers.find(id).orElseThrow(() -> new RaktSetuException("No transfer with ID '" + id + "'"));
    }

    private static void requireStatus(RequestRepository.Row row, RequestRepository.Status expected)
            throws RaktSetuException {
        if (row.status() != expected) {
            throw new RaktSetuException("Request #" + row.id() + " is " + row.status() + ", not " + expected);
        }
    }

    private static String substitutionNote(RequestOutcome outcome) {
        return outcome.allocations().stream().filter(Allocation::isSubstitute)
                .map(a -> "Substitute " + a.donorGroup() + " from " + a.source().getId() + ": "
                        + a.compatibility().reason())
                .collect(Collectors.joining(" "));
    }

    private RequestView view(long id) {
        return view(requests.find(id).orElseThrow(), requests.units(id));
    }

    private RequestView view(RequestRepository.Row r, List<RequestRepository.UnitRow> units) {
        Map<String, List<RequestRepository.UnitRow>> bySource = new LinkedHashMap<>();
        for (RequestRepository.UnitRow u : units) {
            bySource.computeIfAbsent(u.sourceId(), k -> new ArrayList<>()).add(u);
        }
        List<RequestPart> parts = new ArrayList<>();
        BloodNetwork net = network.network();
        LocalDate today = network.today();
        for (Map.Entry<String, List<RequestRepository.UnitRow>> e : bySource.entrySet()) {
            Route route = net.route(e.getKey(), r.hospitalId()).orElse(Route.ZERO);
            String name = net.facilities().stream().filter(f -> f.getId().equals(e.getKey())).map(Facility::getName)
                    .findFirst().orElse(e.getKey());
            List<RequestRepository.UnitRow> list = e.getValue();
            parts.add(new RequestPart(e.getKey(), name, route.distanceKm(), route.travelMinutes(),
                    list.stream().map(u -> u.group().label()).distinct().toList(),
                    list.stream().map(RequestRepository.UnitRow::unitId).toList(),
                    list.stream().map(u -> ChronoUnit.DAYS.between(today, u.expiresOn())).toList(),
                    list.stream().allMatch(RequestRepository.UnitRow::dispatched)));
        }
        return new RequestView(r.id(), r.hospitalId(), r.component().name(), r.group().label(), r.quantity(),
                r.urgency(), r.status().name(), r.raisedOn(), r.raisedBy(), r.updatedOn(), r.note(), parts);
    }

    private static TransferView view(TransferRepository.Row t) {
        return new TransferView(t.id(), t.sourceId(), t.destinationId(), t.units(), t.unitCount(), t.distanceKm(),
                t.travelMinutes(), t.status().name(), t.reason(), t.plannedOn(), t.decidedBy(), t.receivedOn());
    }
}
