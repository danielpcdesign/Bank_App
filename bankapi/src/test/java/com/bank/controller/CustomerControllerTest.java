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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.bank.service.CustomerService;
import com.bank.model.Customer;
import com.bank.model.Role;

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

    // nothing is stubbed on purpose: the request must never get far enough to need it.
    // the 400 alone would not prove that - verifyNoInteractions is what does.


    @Test
    void editCustomer_returns400AndNeverCallsTheService_whenBodyIdDiffersFromPathId() throws Exception
    {
        mockMvc.perform(put("/api/v1/customers/2")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"id\":5,\"username\":\"nina\",\"fullName\":\"Nina Cortez\",\"role\":\"CUSTOMER\",\"password\":\"pw123\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void editCustomer_returns404_whenNoCustomerHasThatId() throws Exception
    {
        // absence is established before the edit is attempted, so this never reaches the service
        when(service.getCustomerById(99)).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/v1/customers/99")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"id\":99,\"username\":\"nina\",\"fullName\":\"Nina Cortez\",\"role\":\"CUSTOMER\",\"password\":\"pw123\"}"))
                .andExpect(status().isNotFound());
    }
    //   when(service.editCustomer(eq(99), any(Customer.class))).thenReturn(Optional.empty())
    //   note eq(99) rather than plain 99: once one argument is a matcher they all must be,
    //   or mockito throws InvalidUseOfMatchersException.

    @Test
    void editCustomer_returns200AndTheUpdatedCustomer_whenTheServiceReturnsIt() throws Exception
    {
        when(service.getCustomerById(99)).thenReturn(Optional.of(new Customer(99, "nina", "Nina Cortez")));
        when(service.editCustomer(eq(99), any(Customer.class))).thenReturn(Optional.of(new Customer(99, "nina", "Nina Cortez")));

        mockMvc.perform(put("/api/v1/customers/99")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"id\":99,\"username\":\"nina\",\"fullName\":\"Nina Cortez\",\"role\":\"CUSTOMER\",\"password\":\"pw123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(99))
                .andExpect(jsonPath("$.username").value("nina"))
                .andExpect(jsonPath("$.fullName").value("Nina Cortez"));
    }

    @Test
    void deleteCustomer_returns204_whenTheServiceReportsTrue() throws Exception
    {
        when(service.getCustomerById(99)).thenReturn(Optional.of(new Customer(99, "nina", "Nina Cortez")));
        when(service.deleteCustomerById(eq(99))).thenReturn(true);

        mockMvc.perform(delete("/api/v1/customers/99"))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteCustomer_returns404_whenTheServiceReportsFalse() throws Exception
    {
        when(service.getCustomerById(99)).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/v1/customers/99"))
                .andExpect(status().isNotFound());

        verify(service, never()).deleteCustomerById(anyInt());
    }

    //----------------------------------------------------------------ROLE----------------------------------------------------------------

    // the role is serialised like any other field. it is data about the customer, and the
    // response says so plainly rather than hiding it behind a permission check that does not exist.
    @Test
    void getAllCustomers_includesTheRoleInTheResponse() throws Exception
    {
        when(service.getAllCustomers()).thenReturn(List.of(new Customer(4, "admin", "Admin User", List.of(), Role.ADMIN)));

        mockMvc.perform(get("/api/v1/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("ADMIN"));
    }

    // ?role= narrows the query and nothing else. it does not identify the caller and grants
    // no privilege - anyone may ask for the admins and will get them.
    @Test
    void getAllCustomers_withARoleParam_asksTheServiceForThatRoleOnly() throws Exception
    {
        when(service.getCustomersByRole(Role.ADMIN))
            .thenReturn(List.of(new Customer(4, "admin", "Admin User", List.of(), Role.ADMIN)));

        mockMvc.perform(get("/api/v1/customers").param("role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].username").value("admin"));

        verify(service, never()).getAllCustomers();
    }

    @Test
    void getAllCustomers_withoutARoleParam_returnsEveryCustomer() throws Exception
    {
        when(service.getAllCustomers()).thenReturn(List.of(
            new Customer(1, "alice", "Alice Smith"),
            new Customer(4, "admin", "Admin User", List.of(), Role.ADMIN)));

        mockMvc.perform(get("/api/v1/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        verify(service, never()).getCustomersByRole(any());
    }

    // an unknown role is a 400 from @RequestParam type conversion, before the method runs -
    // the same mechanism that rejects a non-integer id in a path.
    @Test
    void getAllCustomers_returns400_whenTheRoleIsNotAKnownValue() throws Exception
    {
        mockMvc.perform(get("/api/v1/customers").param("role", "SUPERUSER"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    // role carries the same validation discipline as the other fields, so a body without one
    // is rejected exactly like a body without a username.

    //----------------------------------------------------------------SIGN IN----------------------------------------------------------------

    @Test
    void signIn_returns200AndTheCustomer_whenTheCredentialsMatch() throws Exception
    {
        when(service.signIn("admin", "admin123"))
            .thenReturn(Optional.of(new Customer(4, "admin", "Admin User", List.of(), Role.ADMIN, "admin123")));

        mockMvc.perform(post("/api/v1/customers/signin")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.username").value("admin"));
    }

    // the response carries the customer, so it would carry the password too if WRITE_ONLY
    // were ever removed. signing in is exactly where that would hurt most.
    @Test
    void signIn_neverReturnsThePassword() throws Exception
    {
        when(service.signIn("admin", "admin123"))
            .thenReturn(Optional.of(new Customer(4, "admin", "Admin User", List.of(), Role.ADMIN, "admin123")));

        mockMvc.perform(post("/api/v1/customers/signin")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    // both failures are 401 with no body. a caller must not be able to tell an unknown
    // username from a wrong password, or the endpoint becomes a username oracle.
    @Test
    void signIn_returns401_whenTheUsernameIsUnknown() throws Exception
    {
        when(service.signIn("ghost", "anything")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/customers/signin")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"ghost\",\"password\":\"anything\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(""));
    }

    @Test
    void signIn_returns401AndTheSameEmptyBody_whenThePasswordIsWrong() throws Exception
    {
        when(service.signIn("admin", "wrong")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/customers/signin")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(""));
    }

    @Test
    void signIn_returns400AndNeverCallsTheService_whenThePasswordIsBlank() throws Exception
    {
        mockMvc.perform(post("/api/v1/customers/signin")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"admin\",\"password\":\"   \"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    //----------------------------------------------------------------BODY BINDING----------------------------------------------------------------

    // role, accountIds and password have NO SETTERS. these assert they still arrive on the
    // object handed to the service - the single path capable of breaking every write.

    @Test
    void editCustomer_bindsRoleAccountIdsAndPasswordFromTheBody() throws Exception
    {
        when(service.getCustomerById(5)).thenReturn(Optional.of(new Customer(5, "nina", "Nina Cortez")));
        when(service.editCustomer(eq(5), any(Customer.class)))
            .thenReturn(Optional.of(new Customer(5, "nina", "Nina Cortez")));

        mockMvc.perform(put("/api/v1/customers/5")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"id\":5,\"username\":\"nina\",\"fullName\":\"Nina Cortez\","
                           + "\"role\":\"ADMIN\",\"accountIds\":[103],\"password\":\"pw456\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<Customer> sent = ArgumentCaptor.forClass(Customer.class);
        verify(service).editCustomer(eq(5), sent.capture());

        assertThat(sent.getValue().getRole()).isEqualTo(Role.ADMIN);
        assertThat(sent.getValue().getAccountIds()).containsExactly(103);
        assertThat(sent.getValue().getPassword()).isEqualTo("pw456");
    }

    //----------------------------------------------------------------PASSWORD AND PUT----------------------------------------------------------------

    // a client is never sent a password (WRITE_ONLY), so it cannot echo one back. if PUT
    // required it, editing a customer would be impossible from any client at all.
    @Test
    void editCustomer_succeeds_whenTheBodyOmitsThePassword() throws Exception
    {
        when(service.getCustomerById(5)).thenReturn(Optional.of(new Customer(5, "nina", "Nina Cortez")));
        when(service.editCustomer(eq(5), any(Customer.class)))
            .thenReturn(Optional.of(new Customer(5, "nina", "Nina Cortez")));

        mockMvc.perform(put("/api/v1/customers/5")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"id\":5,\"username\":\"nina\",\"fullName\":\"Nina Cortez\",\"role\":\"CUSTOMER\"}"))
                .andExpect(status().isOk());
    }

    // password lost its @NotBlank so that PUT could work, so CREATE now enforces it in the
    // controller instead. these two are what stop that move from quietly dropping the
    // requirement altogether - a customer created with no password could never sign in.


    // the whole point of the exemption: a client that supplies a password on PUT still
    // changes it. the field is preserved when omitted, not frozen.
    @Test
    void editCustomer_stillAcceptsAPassword_whenTheBodySuppliesOne() throws Exception
    {
        when(service.getCustomerById(5)).thenReturn(Optional.of(new Customer(5, "nina", "Nina Cortez")));
        when(service.editCustomer(eq(5), any(Customer.class)))
            .thenReturn(Optional.of(new Customer(5, "nina", "Nina Cortez")));

        mockMvc.perform(put("/api/v1/customers/5")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"id\":5,\"username\":\"nina\",\"fullName\":\"Nina Cortez\","
                           + "\"role\":\"CUSTOMER\",\"password\":\"newpw\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<Customer> sent = ArgumentCaptor.forClass(Customer.class);
        verify(service).editCustomer(eq(5), sent.capture());
        assertThat(sent.getValue().getPassword()).isEqualTo("newpw");
    }

    //----------------------------------------------------------------CREATE----------------------------------------------------------------

    @Test
    void addCustomer_returns201WithALocationHeader_andTheServerAssignedId() throws Exception
    {
        when(service.addNewCustomer("nina", "pw123", "Nina Cortez"))
            .thenReturn(Optional.of(new Customer(5, "nina", "Nina Cortez", List.of(), Role.CUSTOMER, "pw123")));

        mockMvc.perform(post("/api/v1/customers")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"nina\",\"password\":\"pw123\",\"fullName\":\"Nina Cortez\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/customers/5"))
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    /*
     * THE SMUGGLING TEST, and the point of the whole endpoint.
     *
     * The body carries a role of ADMIN and an id of 99 alongside the legitimate fields.
     * CreateCustomerRequest has no component for either, so there is nothing for them to bind to
     * and they are dropped in transit. The assertion is on what the SERVICE was handed: the
     * three real values and nothing else. A caller cannot create an admin because the
     * request has nowhere to say so, not because something checked.
     */
    @Test
    void addCustomer_ignoresARoleAndIdSmuggledIntoTheBody() throws Exception
    {
        when(service.addNewCustomer("nina", "pw123", "Nina Cortez"))
            .thenReturn(Optional.of(new Customer(5, "nina", "Nina Cortez", List.of(), Role.CUSTOMER, "pw123")));

        mockMvc.perform(post("/api/v1/customers")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"nina\",\"password\":\"pw123\",\"fullName\":\"Nina Cortez\","
                           + "\"role\":\"ADMIN\",\"id\":99,\"accountIds\":[101]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.id").value(5));

        // the service is only ever told the three fields the record carries
        verify(service).addNewCustomer("nina", "pw123", "Nina Cortez");
    }

    @Test
    void addCustomer_returns409_whenTheUsernameIsTaken() throws Exception
    {
        when(service.addNewCustomer("alice", "pw", "Alice Two")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/customers")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"alice\",\"password\":\"pw\",\"fullName\":\"Alice Two\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void addCustomer_returns400AndNeverCallsTheService_whenFullNameIsBlank() throws Exception
    {
        mockMvc.perform(post("/api/v1/customers")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"nina\",\"password\":\"pw123\",\"fullName\":\"   \"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void addCustomer_returns400AndNeverCallsTheService_whenPasswordIsMissing() throws Exception
    {
        mockMvc.perform(post("/api/v1/customers")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"nina\",\"fullName\":\"Nina Cortez\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }
}
