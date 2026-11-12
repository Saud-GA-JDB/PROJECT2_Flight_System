package com.ga.maintenance.config;

import com.ga.maintenance.client.AviationServiceClient;
import com.ga.maintenance.client.IdentityServiceClient;
import com.ga.maintenance.model.*;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.function.Supplier;

@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class MaintenanceSeeder implements CommandLineRunner {
    private final EntityManager em;
    private final IdentityServiceClient identity;
    private final AviationServiceClient aviation;
    private final Long airplaneId;
    private final String authorization;

    public MaintenanceSeeder(EntityManager em, IdentityServiceClient identity, AviationServiceClient aviation,
            @Value("${app.seed.airplane-id:0}") Long airplaneId, @Value("${app.seed.authorization:}") String authorization) {
        this.em = em;
        this.identity = identity;
        this.aviation = aviation;
        this.airplaneId = airplaneId;
        this.authorization = authorization;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (airplaneId <= 0 || authorization.isBlank())
            throw new IllegalStateException("Maintenance seeding needs an airplane ID and its airline admin's bearer Authorization header.");
        var scope = aviation.getAirplaneScope(authorization, airplaneId);
        em.createNativeQuery("SELECT pg_advisory_xact_lock(900003)").getSingleResult();
        MaintenanceProvider provider = findOrCreate(MaintenanceProvider.class, "contactEmail", "seed.maintenance@mailsac.com", () -> {
            MaintenanceProvider value = new MaintenanceProvider();
            value.setName("Sample Gulf Maintenance");
            value.setContactEmail("seed.maintenance@mailsac.com");
            return value;
        });
        if (em.createQuery("select count(c) from MaintenanceContract c where c.providerId = :provider and c.airlineId = :airline", Long.class)
                .setParameter("provider", provider.getId()).setParameter("airline", scope.airlineId()).getSingleResult() == 0) {
            MaintenanceContract contract = new MaintenanceContract();
            contract.setProviderId(provider.getId());
            contract.setAirlineId(scope.airlineId());
            em.persist(contract);
        }
        for (MaintenanceEmployee.MaintenanceRole role : MaintenanceEmployee.MaintenanceRole.values()) {
            var account = identity.accountByEmail("maintenance." + role.name().toLowerCase() + "@mailsac.com");
            if (!"MAINTENANCE_EMPLOYEE".equals(account.role()))
                throw new IllegalStateException("The seed account must be a Maintenance employee.");
            findOrCreate(MaintenanceEmployee.class, "userId", account.userId(), () -> {
                MaintenanceEmployee employee = new MaintenanceEmployee();
                employee.setUserId(account.userId());
                employee.setProvider(provider);
                employee.setMaintenanceRole(role);
                if (role == MaintenanceEmployee.MaintenanceRole.TECHNICIAN) employee.setLicenseNumber("SEED-TECH-001");
                return employee;
            });
        }
        findOrCreate(MaintenanceRecord.class, "description", "Project 3 sample routine maintenance", () -> {
            MaintenanceRecord record = new MaintenanceRecord();
            record.setAirplaneId(scope.airplaneId());
            record.setProvider(provider);
            record.setDescription("Project 3 sample routine maintenance");
            return record;
        });
    }

    private <T> T findOrCreate(Class<T> type, String field, Object key, Supplier<T> factory) {
        return em.createQuery("select e from " + type.getSimpleName() + " e where e." + field + " = :key", type)
                .setParameter("key", key).getResultStream().findFirst().orElseGet(() -> {
                    T entity = factory.get();
                    em.persist(entity);
                    return entity;
                });
    }
}
