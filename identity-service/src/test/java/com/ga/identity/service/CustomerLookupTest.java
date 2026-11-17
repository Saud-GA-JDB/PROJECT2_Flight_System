package com.ga.identity.service;

import com.ga.common.contract.identity.CustomerResponse;
import com.ga.common.web.exception.InvalidInformationException;
import com.ga.identity.model.User;
import com.ga.identity.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerLookupTest {
    private final UserRepository users = mock(UserRepository.class);
    private final AccountService service = new AccountService(users, null);

    @Test
    void searchNormalizesQueryAndOnlyRequestsActiveCustomers() {
        var customer = new CustomerResponse(40L, "Ali Ahmed", "ali@example.test");
        when(users.findCustomers("ali", User.Role.CUSTOMER, User.Status.ACTIVE, PageRequest.of(1, 20)))
                .thenReturn(new PageImpl<>(List.of(customer), PageRequest.of(1, 20), 21));
        var response = service.findCustomers(" ALI ", 1, 20);
        assertEquals(List.of(customer), response.content());
        assertEquals(21L, response.totalElements());
        assertEquals(2L, response.totalPages());
    }

    @Test
    void invalidQueryAndPagingAreRejectedBeforeSearching() {
        assertThrows(InvalidInformationException.class, () -> service.findCustomers(null, 0, 20));
        assertThrows(InvalidInformationException.class, () -> service.findCustomers(" ", 0, 20));
        assertThrows(InvalidInformationException.class, () -> service.findCustomers("a".repeat(101), 0, 20));
        assertThrows(InvalidInformationException.class, () -> service.findCustomers("ali", -1, 20));
        assertThrows(InvalidInformationException.class, () -> service.findCustomers("ali", 0, 0));
        assertThrows(InvalidInformationException.class, () -> service.findCustomers("ali", 0, 101));
        assertThrows(InvalidInformationException.class, () -> service.findCustomers("ali", Integer.MAX_VALUE, 20));
        verifyNoInteractions(users);
    }
}
