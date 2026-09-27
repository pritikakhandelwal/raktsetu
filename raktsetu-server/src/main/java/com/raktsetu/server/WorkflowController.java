package com.raktsetu.server;

import com.raktsetu.domain.exception.RaktSetuException;
import com.raktsetu.server.ApiModels.BloodRequestInput;
import com.raktsetu.server.ApiModels.DiscardInput;
import com.raktsetu.server.ApiModels.NotificationList;
import com.raktsetu.server.ApiModels.PlanInput;
import com.raktsetu.server.ApiModels.RequestView;
import com.raktsetu.server.ApiModels.TransferView;
import com.raktsetu.server.security.AppUser;
import com.raktsetu.server.security.CurrentUsers;
import com.raktsetu.server.security.Role;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Requests, transfers, discards and notifications. Permission checks are explained per endpoint. */
@RestController
@RequestMapping("/api")
public class WorkflowController {

    private final WorkflowService workflows;
    private final CurrentUsers users;

    public WorkflowController(WorkflowService workflows, CurrentUsers users) {
        this.workflows = workflows;
        this.users = users;
    }

    /** Admins see all requests, hospitals their own, centres those they supply. */
    @GetMapping("/requests")
    public List<RequestView> requests(Authentication auth) {
        return workflows.requests(users.of(auth));
    }

    /** Hospital staff raise requests for their own hospital; admins for any. */
    @PostMapping("/requests")
    public RequestView raise(@RequestBody BloodRequestInput input, Authentication auth) throws RaktSetuException {
        AppUser user = users.of(auth);
        user.requireActingFor(input == null ? null : input.hospitalId(), Role.HOSPITAL_STAFF);
        return workflows.raise(input, user);
    }

    /** Supplying centres (or admins) dispatch. */
    @PostMapping("/requests/{id}/dispatch")
    public RequestView dispatch(@PathVariable long id, Authentication auth) throws RaktSetuException {
        return workflows.dispatch(id, users.of(auth));
    }

    /** The requesting hospital (or an admin) confirms receipt. */
    @PostMapping("/requests/{id}/receive")
    public RequestView receive(@PathVariable long id, Authentication auth) throws RaktSetuException {
        return workflows.receive(id, users.of(auth));
    }

    /** The requesting hospital (or an admin) cancels before dispatch. */
    @PostMapping("/requests/{id}/cancel")
    public RequestView cancel(@PathVariable long id, Authentication auth) throws RaktSetuException {
        return workflows.cancel(id, users.of(auth));
    }

    @GetMapping("/transfers")
    public List<TransferView> transfers() {
        return workflows.transfers();
    }

    /** Admins only. */
    @PostMapping("/plans")
    public List<TransferView> plan(@RequestBody PlanInput input, Authentication auth) {
        users.of(auth).requireAdmin();
        return workflows.plan(input == null ? null : input.strategy());
    }

    /** Admins only. */
    @PostMapping("/transfers/{id}/approve")
    public TransferView approve(@PathVariable String id, Authentication auth) throws RaktSetuException {
        AppUser user = users.of(auth);
        user.requireAdmin();
        return workflows.approve(id, user);
    }

    /** Admins only. */
    @PostMapping("/transfers/{id}/reject")
    public TransferView reject(@PathVariable String id, Authentication auth) throws RaktSetuException {
        AppUser user = users.of(auth);
        user.requireAdmin();
        return workflows.reject(id, user);
    }

    /** The destination centre (or an admin) confirms arrival. */
    @PostMapping("/transfers/{id}/receive")
    public TransferView receiveTransfer(@PathVariable String id, Authentication auth) throws RaktSetuException {
        return workflows.receiveTransfer(id, users.of(auth));
    }

    /** Centre staff discard damaged units at their own centre. */
    @PostMapping("/facilities/{id}/units/{unitId}/discard")
    public ResponseEntity<Void> discard(@PathVariable String id, @PathVariable String unitId,
                                        @RequestBody DiscardInput input, Authentication auth)
            throws RaktSetuException {
        users.of(auth).requireActingFor(id, Role.CENTRE_STAFF);
        workflows.discard(id, unitId, input == null ? null : input.reason());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/notifications")
    public NotificationList notifications(Authentication auth) {
        return workflows.notifications(users.of(auth));
    }

    @PostMapping("/notifications/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable long id, Authentication auth) {
        workflows.markRead(id, users.of(auth));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/notifications/read-all")
    public ResponseEntity<Void> markAllRead(Authentication auth) {
        workflows.markAllRead(users.of(auth));
        return ResponseEntity.noContent().build();
    }
}
