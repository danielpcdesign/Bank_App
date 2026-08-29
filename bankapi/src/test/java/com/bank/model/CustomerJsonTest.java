package com.bank.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

/*
 * The path every write goes through, and until now the only one with no test behind it.
 *
 * Customer has NO SETTERS for role, accountIds or password. Binding works anyway because
 * jackson infers mutators: the public getters make the properties visible, and it writes the
 * private fields directly. That is a default (INFER_PROPERTY_MUTATORS), not a guarantee -
 * turn it off, or add @JsonIgnore to a getter, and every POST and PUT starts silently
 * dropping fields while still returning 201 and 200. These tests are what would catch it.
 *
 * A real ObjectMapper, not a mock. Note the import: boot 4 ships jackson 3, so databind is
 * tools.jackson.databind while the annotations stayed at com.fasterxml.jackson.annotation.
 */
class CustomerJsonTest
{
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void bindsRoleFromTheBody_despiteThereBeingNoSetter()
    {
        Customer bound = mapper.readValue(
            "{\"id\":4,\"username\":\"admin\",\"fullName\":\"Admin User\",\"role\":\"ADMIN\",\"password\":\"admin123\"}",
            Customer.class);

        assertThat(bound.getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void bindsAccountIdsFromTheBody_despiteThereBeingNoSetter()
    {
        Customer bound = mapper.readValue(
            "{\"id\":1,\"username\":\"alice\",\"fullName\":\"Alice Smith\",\"role\":\"CUSTOMER\","
                + "\"accountIds\":[101,102],\"password\":\"pw\"}",
            Customer.class);

        assertThat(bound.getAccountIds()).containsExactly(101, 102);
    }

    @Test
    void bindsPasswordFromTheBody_becauseWriteOnlyStillAllowsReadingIn()
    {
        Customer bound = mapper.readValue(
            "{\"id\":1,\"username\":\"alice\",\"fullName\":\"Alice Smith\",\"role\":\"CUSTOMER\","
                + "\"password\":\"alice123\"}",
            Customer.class);

        assertThat(bound.getPassword()).isEqualTo("alice123");
    }

    // an absent accountIds must arrive as an empty list rather than null, or every caller
    // has to null-check a collection. the constructor is what guarantees it.
    @Test
    void bindsAnAbsentAccountIdsAsAnEmptyList_notNull()
    {
        Customer bound = mapper.readValue(
            "{\"id\":1,\"username\":\"alice\",\"fullName\":\"Alice Smith\",\"role\":\"CUSTOMER\",\"password\":\"pw\"}",
            Customer.class);

        assertThat(bound.getAccountIds()).isEmpty();
    }

    //----------------------------------------------------------------OUTPUT----------------------------------------------------------------

    // the single most important assertion in this file. without WRITE_ONLY, GET /api/v1/customers
    // returns every password in the system to any caller.
    @Test
    void neverWritesThePasswordOut()
    {
        String json = mapper.writeValueAsString(
            new Customer(1, "alice", "Alice Smith", List.of(101), Role.CUSTOMER, "alice123"));

        assertThat(json).doesNotContain("alice123");
        assertThat(json).doesNotContain("password");
    }

    @Test
    void writesRoleAndAccountIdsOut()
    {
        String json = mapper.writeValueAsString(
            new Customer(4, "admin", "Admin User", List.of(101), Role.ADMIN, "admin123"));

        assertThat(json).contains("\"role\":\"ADMIN\"");
        assertThat(json).contains("\"accountIds\":[101]");
    }
}
