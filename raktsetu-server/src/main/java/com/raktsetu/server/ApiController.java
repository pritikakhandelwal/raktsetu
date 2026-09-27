package com.raktsetu.server;

import com.raktsetu.domain.exception.RaktSetuException;
import com.raktsetu.server.ApiModels.AddUnitsInput;
import com.raktsetu.server.ApiModels.AddedUnits;
import com.raktsetu.server.ApiModels.AuditView;
import com.raktsetu.server.ApiModels.CompatibilityInput;
import com.raktsetu.server.ApiModels.CompatibilityView;
import com.raktsetu.server.ApiModels.ComparisonView;
import com.raktsetu.server.ApiModels.FacilityInput;
import com.raktsetu.server.ApiModels.FacilityStock;
import com.raktsetu.server.ApiModels.FacilityView;
import com.raktsetu.server.ApiModels.NetworkStock;
import com.raktsetu.server.ApiModels.RiskView;
import com.raktsetu.server.ApiModels.RouteInput;
import com.raktsetu.server.ApiModels.RouteView;
import com.raktsetu.server.ApiModels.StrategyView;
import com.raktsetu.server.ApiModels.Summary;
import com.raktsetu.server.ApiModels.WastageView;
import com.raktsetu.server.security.CurrentUsers;
import com.raktsetu.server.security.Role;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The REST API. Every endpoint needs a signed-in user (see {@code SecurityConfig}). Anyone signed in may look;
 * changes are limited by role: centre staff act only for their centre, hospital staff only for their hospital, and
 * facilities and routes are managed by administrators. Requests, transfers and notifications are in {@link WorkflowController}.
 * Errors are mapped by {@link ApiErrors}.
 */
@RestController
@RequestMapping("/api")
public class ApiController {

    private final NetworkService service;
    private final CurrentUsers users;

    public ApiController(NetworkService service, CurrentUsers users) {
        this.service = service;
        this.users = users;
    }

    @GetMapping("/summary")
    public Summary summary() throws RaktSetuException {
        return service.summary();
    }

    @GetMapping("/facilities")
    public List<FacilityView> facilities() {
        return service.facilities();
    }

    @GetMapping("/facilities/{id}/stock")
    public FacilityStock facilityStock(@PathVariable String id) throws RaktSetuException {
        return service.facilityStock(id);
    }

    @PostMapping("/facilities/{id}/units")
    public AddedUnits addUnits(@PathVariable String id, @RequestBody AddUnitsInput input, Authentication auth)
            throws RaktSetuException {
        users.of(auth).requireActingFor(id, Role.CENTRE_STAFF);
        return service.addUnits(id, input);
    }

    @PostMapping("/facilities/{id}/report")
    public FacilityView recordReport(@PathVariable String id, Authentication auth) throws RaktSetuException {
        users.of(auth).requireActingFor(id, Role.CENTRE_STAFF);
        return service.recordReport(id);
    }

    @GetMapping("/stock")
    public NetworkStock networkStock() {
        return service.networkStock();
    }

    @PostMapping("/compatibility")
    public CompatibilityView compatibility(@RequestBody CompatibilityInput input) {
        return service.compatibility(input);
    }

    @GetMapping("/risks")
    public List<RiskView> risks() {
        return service.risks();
    }

    @GetMapping("/strategies")
    public List<StrategyView> strategies() {
        return service.strategies();
    }

    @GetMapping("/strategies/compare")
    public List<ComparisonView> compare() {
        return service.compare();
    }

    @GetMapping("/audit")
    public AuditView audit(@RequestParam(defaultValue = "20") int limit) {
        return service.audit(limit);
    }

    @PostMapping("/facilities")
    public FacilityView addFacility(@RequestBody FacilityInput input, Authentication auth) {
        users.of(auth).requireAdmin();
        return service.addFacility(input);
    }

    @PutMapping("/facilities/{id}")
    public FacilityView updateFacility(@PathVariable String id, @RequestBody FacilityInput input, Authentication auth)
            throws RaktSetuException {
        users.of(auth).requireAdmin();
        return service.updateFacility(id, input);
    }

    @GetMapping("/facilities/{id}/routes")
    public List<RouteView> routes(@PathVariable String id) throws RaktSetuException {
        return service.routes(id);
    }

    @PutMapping("/routes/{fromId}/{toId}")
    public RouteView setRoute(@PathVariable String fromId, @PathVariable String toId, @RequestBody RouteInput input,
                              Authentication auth) throws RaktSetuException {
        users.of(auth).requireAdmin();
        return service.setRoute(fromId, toId, input);
    }

    @GetMapping("/wastage")
    public WastageView wastage() {
        return service.wastage();
    }

}
