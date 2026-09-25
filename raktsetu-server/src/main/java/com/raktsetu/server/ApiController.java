package com.raktsetu.server;

import com.raktsetu.domain.exception.RaktSetuException;
import com.raktsetu.server.ApiModels.AddUnitsInput;
import com.raktsetu.server.ApiModels.AddedUnits;
import com.raktsetu.server.ApiModels.AuditView;
import com.raktsetu.server.ApiModels.BloodRequestInput;
import com.raktsetu.server.ApiModels.CompatibilityInput;
import com.raktsetu.server.ApiModels.CompatibilityView;
import com.raktsetu.server.ApiModels.ComparisonView;
import com.raktsetu.server.ApiModels.DateInput;
import com.raktsetu.server.ApiModels.FacilityStock;
import com.raktsetu.server.ApiModels.FacilityView;
import com.raktsetu.server.ApiModels.NetworkStock;
import com.raktsetu.server.ApiModels.PlanInput;
import com.raktsetu.server.ApiModels.RequestResult;
import com.raktsetu.server.ApiModels.RiskView;
import com.raktsetu.server.ApiModels.SimulationInput;
import com.raktsetu.server.ApiModels.SimulationView;
import com.raktsetu.server.ApiModels.StrategyView;
import com.raktsetu.server.ApiModels.Summary;
import com.raktsetu.server.ApiModels.TransferView;
import com.raktsetu.server.ApiModels.WastageView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The REST API. Every endpoint delegates to {@link NetworkService}; errors are mapped by {@link ApiErrors}. */
@RestController
@RequestMapping("/api")
public class ApiController {

    private final NetworkService service;

    public ApiController(NetworkService service) {
        this.service = service;
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
    public AddedUnits addUnits(@PathVariable String id, @RequestBody AddUnitsInput input) throws RaktSetuException {
        return service.addUnits(id, input);
    }

    @PostMapping("/facilities/{id}/report")
    public FacilityView recordReport(@PathVariable String id) throws RaktSetuException {
        return service.recordReport(id);
    }

    @GetMapping("/stock")
    public NetworkStock networkStock() {
        return service.networkStock();
    }

    @PostMapping("/requests")
    public RequestResult request(@RequestBody BloodRequestInput input) throws RaktSetuException {
        return service.request(input);
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

    @PostMapping("/plans")
    public List<TransferView> plan(@RequestBody PlanInput input) {
        return service.plan(input == null ? null : input.strategy());
    }

    @GetMapping("/transfers")
    public List<TransferView> transfers() {
        return service.transfers();
    }

    @PostMapping("/transfers/{id}/approve")
    public TransferView approve(@PathVariable String id) throws RaktSetuException {
        return service.approve(id);
    }

    @PostMapping("/transfers/{id}/reject")
    public TransferView reject(@PathVariable String id) throws RaktSetuException {
        return service.reject(id);
    }

    @GetMapping("/audit")
    public AuditView audit(@RequestParam(defaultValue = "20") int limit) throws RaktSetuException {
        return service.audit(limit);
    }

    @PostMapping("/simulations")
    public SimulationView simulate(@RequestBody(required = false) SimulationInput input) throws RaktSetuException {
        return service.simulate(input == null ? null : input.days());
    }

    @GetMapping("/wastage")
    public WastageView wastage() {
        return service.wastage();
    }

    @PostMapping("/date")
    public Summary advanceDate(@RequestBody DateInput input) throws RaktSetuException {
        return service.advanceTo(input == null ? null : input.today());
    }
}
