package com.bank.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.bank.service.CustomerService;
import com.bank.model.Customer;

import java.util.List;
import java.util.Optional;

// boots the web layer only - the controller, jackson, validation, the routing table.
// no service, no repository, no mongo autoconfiguration, no atlas.
// the service is a mock, so these tests prove the HTTP translation and nothing below it.
@WebMvcTest(CustomerController.class)
class CustomerControllerTest
{
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomerService service;

    // ---------------------------------------------------------------------
    // behaviours to drive out. write the test, run it, watch it FAIL, then
    // confirm the controller already satisfies it. a test that has never been
    // red proves nothing - that is the whole lesson from the service tests.
    //
    // GET /customers
    //   returns 200 and the list the service handed over
    @Test
    void getAllCustomers_returnsTheListFromTheService() throws Exception
    {
        when(service.getAllCustomers()).thenReturn(List.of(new Customer(1, "alice", "Alice Smith")));
        mockMvc.perform(get("/api/v1/customers"))
                .andExpect(status().isOk())
                .andExpect(content().json("[{\"id\":1,\"username\":\"alice\",\"fullName\":\"Alice Smith\"}]"));
    }

    // jsonPath navigates the body without deserializing it. $ is the root,
    // $.username a field, $[0].id the first element of an array.
    @Test
    void getCustomerById_returns200AndTheCustomer_whenTheServiceFindsIt() throws Exception
    {
        when(service.getCustomerById(2)).thenReturn(Optional.of(new Customer(2, "bob", "Bob Jones")));

        mockMvc.perform(get("/api/v1/customers/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.username").value("bob"));
    }

    // "not found" is a fact the service reports as an empty Optional.
    // "404" is an http decision only the controller makes. this test is that translation.
    @Test
    void getCustomerById_returns404_whenTheServiceReturnsEmpty() throws Exception
    {
        when(service.getCustomerById(99)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/customers/99"))
                .andExpect(status().isNotFound());
    }

    // any(Customer.class) is not laziness - it is required. jackson deserializes the json
    // into its own Customer instance, and Customer does not override equals(), so a literal
    // argument would be compared by reference and could never match.
    @Test
    void addCustomer_returns201WithALocationHeader_whenTheServiceCreatesIt() throws Exception
    {
        when(service.addNewCustomer(any(Customer.class))).thenReturn(true);

        mockMvc.perform(post("/api/v1/customers")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"id\":4,\"username\":\"nina\",\"fullName\":\"Nina Cortez\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/customers/4"))
                .andExpect(jsonPath("$.username").value("nina"));
    }

    // nothing is stubbed on purpose: the request must never get far enough to need it.
    // the 400 alone would not prove that - verifyNoInteractions is what does.
    @Test
    void addCustomer_returns400AndNeverCallsTheService_whenUsernameIsBlank() throws Exception
    {
        mockMvc.perform(post("/api/v1/customers")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"id\":4,\"username\":\"   \",\"fullName\":\"Nina Cortez\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void addCustomer_returns409_whenTheIdIsAlreadyTaken() throws Exception
    {
        when(service.addNewCustomer(any(Customer.class))).thenReturn(false);

        mockMvc.perform(post("/api/v1/customers")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"id\":4,\"username\":\"nina\",\"fullName\":\"Nina Cortez\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void editCustomer_returns400AndNeverCallsTheService_whenBodyIdDiffersFromPathId() throws Exception
    {
        mockMvc.perform(put("/api/v1/customers/2")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"id\":5,\"username\":\"nina\",\"fullName\":\"Nina Cortez\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void editCustomer_returns404_whenTheServiceReturnsEmpty() throws Exception
    {
        when(service.editCustomer(eq(99), any(Customer.class))).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/v1/customers/99")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"id\":99,\"username\":\"nina\",\"fullName\":\"Nina Cortez\"}"))
                .andExpect(status().isNotFound());
    }
    //   when(service.editCustomer(eq(99), any(Customer.class))).thenReturn(Optional.empty())
    //   note eq(99) rather than plain 99: once one argument is a matcher they all must be,
    //   or mockito throws InvalidUseOfMatchersException.

    @Test
    void editCustomer_returns200AndTheUpdatedCustomer_whenTheServiceReturnsIt() throws Exception
    {
        when(service.editCustomer(eq(99), any(Customer.class))).thenReturn(Optional.of(new Customer(99, "nina", "Nina Cortez")));

        mockMvc.perform(put("/api/v1/customers/99")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"id\":99,\"username\":\"nina\",\"fullName\":\"Nina Cortez\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(99))
                .andExpect(jsonPath("$.username").value("nina"))
                .andExpect(jsonPath("$.fullName").value("Nina Cortez"));
    }

    @Test
    void deleteCustomer_returns204_whenTheServiceReportsTrue() throws Exception
    {
        when(service.deleteCustomerById(eq(99))).thenReturn(true);

        mockMvc.perform(delete("/api/v1/customers/99"))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteCustomer_returns404_whenTheServiceReportsFalse() throws Exception
    {
        when(service.deleteCustomerById(eq(99))).thenReturn(false);

        mockMvc.perform(delete("/api/v1/customers/99"))
                .andExpect(status().isNotFound());
    }
}
