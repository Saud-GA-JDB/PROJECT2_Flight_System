package com.ga.saudsFlightSystem.service;

import com.ga.saudsFlightSystem.model.AirNavigationServiceProvider;
import com.ga.saudsFlightSystem.model.ControlFacility;
import com.ga.saudsFlightSystem.model.request.response.FacilityListResponse;
import com.ga.saudsFlightSystem.model.request.response.FacilityResponse;
import com.ga.saudsFlightSystem.model.request.response.ProviderListResponse;
import com.ga.saudsFlightSystem.model.request.response.ProviderResponse;
import com.ga.saudsFlightSystem.repository.AirNavigationServiceProviderRepository;
import com.ga.saudsFlightSystem.repository.ControlFacilityRepository;
import lombok.AllArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@AllArgsConstructor
public class ATCCacheService {
    private AirNavigationServiceProviderRepository providerRepository;
    private ControlFacilityRepository facilityRepository;

    @Cacheable(value = "navigationProviders", key = "#page + ':' + #size")
    public ProviderListResponse getProviders(int page, int size) {
        List<ProviderResponse> content = new ArrayList<>();

        for (AirNavigationServiceProvider provider :
                providerRepository.findProviders(size, (long) page * size)) {
            content.add(new ProviderResponse(
                    provider.getId(),
                    provider.getName(),
                    provider.getContactEmail()
            ));
        }

        long totalElements = providerRepository.count();
        return new ProviderListResponse(
                content, page, size, totalElements, totalPages(totalElements, size)
        );
    }

    @Cacheable(
            value = "controlFacilities",
            key = "#providerId + ':' + #page + ':' + #size"
    )
    public FacilityListResponse getFacilities(Long providerId, int page, int size) {
        List<FacilityResponse> content = new ArrayList<>();

        for (ControlFacility facility :
                facilityRepository.findByProviderId(providerId, size, (long) page * size)) {
            content.add(new FacilityResponse(
                    facility.getId(),
                    facility.getProvider().getId(),
                    facility.getAirport().getId(),
                    facility.getName(),
                    facility.getFacilityType()
            ));
        }

        long totalElements = facilityRepository.countByProvider_Id(providerId);
        return new FacilityListResponse(
                content, page, size, totalElements, totalPages(totalElements, size)
        );
    }

    @EventListener(ApplicationReadyEvent.class)
    @CacheEvict(value = "navigationProviders", allEntries = true)
    public void evictProviders() {
        // Spring removes all cached provider pages.
    }

    @EventListener(ApplicationReadyEvent.class)
    @CacheEvict(value = "controlFacilities", allEntries = true)
    public void evictFacilities() {
        // Spring removes all cached facility pages.
    }

    private long totalPages(long totalElements, int size) {
        long totalPages = totalElements / size;
        if (totalElements % size != 0)
            totalPages++;
        return totalPages;
    }
}
