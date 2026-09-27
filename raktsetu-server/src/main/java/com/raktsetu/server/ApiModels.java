package com.raktsetu.server;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** The JSON shapes of the REST API. Requests are validated in {@link NetworkService}. */
public final class ApiModels {

    private ApiModels() {
    }

    // ------------------------------------------------------------------ responses

    public record Summary(LocalDate today, int storingFacilities, int hospitals, int unitsInStock, int urgentUnits,
                          int atRiskUnits, int unitsWasted, int pendingTransfers, boolean auditIntact) {
    }

    public record FacilityView(String id, String name, String type, LocalDate lastReportedOn, Long daysSinceReport,
                               String freshness, int units, int urgentUnits, int atRiskUnits) {
    }

    public record StockLine(String component, String group, int units, LocalDate soonestExpiry, long daysLeft,
                            boolean urgent) {
    }

    public record FacilityStock(FacilityView facility, List<StockLine> lines) {
    }

    public record NetworkStockRow(String component, List<Integer> counts, int total) {
    }

    public record NetworkStock(List<String> groups, List<NetworkStockRow> rows, List<String> low) {
    }

    /** The units one centre supplies for a request. */
    public record RequestPart(String sourceId, String sourceName, int distanceKm, int travelMinutes,
                              List<String> donorGroups, List<String> unitIds, List<Long> daysLeft,
                              boolean dispatched) {
    }

    /** A hospital request and where it is in its life (ALLOCATED, DISPATCHED, RECEIVED, CANCELLED, UNMET). */
    public record RequestView(long id, String hospitalId, String component, String group, int quantity,
                              String urgency, String status, LocalDate raisedOn, String raisedBy, LocalDate updatedOn,
                              String note, List<RequestPart> parts) {
    }

    public record NotificationView(long id, LocalDate createdOn, String kind, String message, boolean read) {
    }

    public record NotificationList(int unread, List<NotificationView> items) {
    }

    public record CompatibilityView(boolean allowed, String level, String reason) {
    }

    public record RiskView(String facilityId, String facilityName, String component, String group, int atRiskUnits,
                           int usableStock, long soonestDaysLeft, double averageDaily, String reason) {
    }

    public record StrategyView(String name, String description) {
    }

    public record ComparisonView(String strategy, int transfers, int unitsRescued, int stillAtRisk, int totalKm) {
    }

    public record TransferView(String id, String sourceId, String destinationId, String units, int unitCount,
                               int distanceKm, int travelMinutes, String status, String reason, LocalDate plannedOn,
                               String decidedBy, LocalDate receivedOn) {
    }

    public record AuditEntry(long sequence, LocalDate date, String type, String facilityId, String counterparty,
                             String component, String group, int units, String note, String hash) {
    }

    public record AuditView(int total, boolean intact, String message, List<AuditEntry> entries) {
    }

    public record SimulationRow(String policy, int unitsExpired, Map<String, Integer> expiredByComponent,
                                int requestsFulfilled, int requestsUnmet, int unitsTransferred,
                                double wastageReductionPercent) {
    }

    public record SimulationView(int days, long seed, LocalDate start, List<SimulationRow> results) {
    }

    public record WastageView(int total, Map<String, Integer> byComponent, Map<String, Integer> byFacility) {
    }

    public record AddedUnits(List<String> unitIds, LocalDate expiresOn) {
    }

    public record ApiError(String error, String message) {
    }

    // ------------------------------------------------------------------ requests

    public record BloodRequestInput(String hospitalId, String component, String group, Integer quantity,
                                    String urgency) {
    }

    public record CompatibilityInput(String component, String donor, String recipient) {
    }

    public record PlanInput(String strategy) {
    }

    public record AddUnitsInput(String component, String group, LocalDate collectedOn, Integer quantity) {
    }

    public record SimulationInput(Integer days) {
    }

    public record DateInput(LocalDate today) {
    }

    public record DiscardInput(String reason) {
    }
}
