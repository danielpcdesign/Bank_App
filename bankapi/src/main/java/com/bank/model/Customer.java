package com.bank.model;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Document(collection = "customers")
public class Customer
{
    @Id
    @NotNull
    @Positive
    private Integer id;

    @NotBlank
    private String username;
    
    @NotBlank
    private String fullName;

    // the accounts this customer owns, by id. not embedded Account objects - accounts are
    // their own model with their own collection and endpoints, and embedding them here
    // would quietly make that untrue by giving each account a second home.
    private List<Integer> accountIds;

    // required like every other field on this class. see Role: it is stored and returned,
    // and it gates nothing - there is no authentication here for it to gate against.
    @NotNull
    private Role role;

    /*
     * A PLAINTEXT PASSWORD. Nothing hashes it, nothing salts it, and the name carries no
     * security property whatsoever - it is the literal string the caller sent, stored as
     * given and comparable with equals(). Hashing is deliberately deferred to phase 10;
     * this field is not a placeholder that "will be fine", it is a known weakness with a
     * scheduled fix. Anyone with read access to the database has every password.
     *
     * WRITE_ONLY is the one thing standing between that and something far worse. Jackson
     * binds this field IN from a request body and never writes it OUT, so it cannot appear
     * in a response. Remove the annotation and GET /api/v1/customers hands the entire
     * credential set to anyone who asks - one unauthenticated request for every password in
     * the system. The annotation is load-bearing; treat it as part of the field.
     *
     * NOT @NotBlank, and that is the one thing here that looks like an oversight and is not.
     *
     * The annotation would apply to every route that binds a Customer, and PUT is one of
     * them. Because WRITE_ONLY means a client is never SENT a password, a client editing a
     * username has no value to send back - so a required password made PUT return 400 for
     * every caller, and editing a customer was impossible from any client. Verified, not
     * assumed: it was a 400, not a silent blanking.
     *
     * Two individually correct decisions collided. Both are kept, and the requirement moved
     * instead: CustomerController enforces the password on CREATE, where the caller genuinely
     * has one. See CustomerRepository.editCustomer for what PUT does with it.
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    public Customer()
    {

    }

    // most customers are customers. the shorter constructors exist so seed data and tests
    // that predate roles keep reading cleanly; anything role-specific uses the full one.
    public Customer(Integer id, String username, String fullName)
    {
        this(id, username, fullName, null, Role.CUSTOMER);
    }

    public Customer(Integer id, String username, String fullName, List<Integer> accountIds)
    {
        this(id, username, fullName, accountIds, Role.CUSTOMER);
    }

    public Customer(Integer id, String username, String fullName, List<Integer> accountIds, Role role)
    {
        this(id, username, fullName, accountIds, role, null);
    }

    public Customer(Integer id, String username, String fullName, List<Integer> accountIds, Role role, String password)
    {
        this.password = password;
        this.id = id;
        this.username = username;
        this.fullName = fullName;
        this.role = role;
        // a customer with no accounts holds an empty list, never null. documents written
        // before this field existed have no value for it, so null arrives from mongo too.
        this.accountIds = (accountIds == null) ? new ArrayList<>() : new ArrayList<>(accountIds);
    }

    //----------------------------------------------------------------GETTERS----------------------------------------------------------------

    public Integer getId()
    {
        return id;
    }

    public String getUsername()
    {
        return username;
    }

    public String getFullName()
    {
        return fullName;
    }

    // copy, not the field. the list is this customer's record of what it owns, and a
    // caller that could add to it would be opening an account without going through the service.
    public List<Integer> getAccountIds()
    {
        return (accountIds == null) ? List.of() : List.copyOf(accountIds);
    }

    public Role getRole()
    {
        return role;
    }

    // needed so sign-in can compare it. safe to expose as a getter ONLY because WRITE_ONLY
    // stops jackson using it for output - without that annotation this getter is the leak.
    public String getPassword()
    {
        return password;
    }

    //----------------------------------------------------------------SETTERS----------------------------------------------------------------

    public void setFullName(String fullName)
    {
        this.fullName = fullName;
    }

    public void setUsername(String username)
    {
        this.username = username;
    }

    /*
     * The ONLY safe way to change a customer's account list, and the reason it exists.
     *
     * The account endpoints used to update the list by CONSTRUCTING A REPLACEMENT customer -
     * new Customer(id, username, fullName, ids) - which quietly dropped every field that
     * constructor does not take. It defaults role to CUSTOMER and password to null, so
     * opening an account destroyed the owner's password and demoted any admin who owned one.
     * The endpoint returned 201 while doing it, and the password was unrecoverable because
     * it is WRITE_ONLY and had never been disclosed.
     *
     * Fetch the stored customer, call this, and save it. Mutating one field cannot lose the
     * others, because the others are never restated - which is the whole category of bug
     * gone, not this one instance of it. Do NOT go back to rebuilding a Customer to change
     * its accounts: with six constructors the compiler cannot warn you that you picked one
     * which silently discards a field, and a seventh field tomorrow would break every
     * shorter call site the same silent way.
     */
    public void setAccountIds(List<Integer> accountIds)
    {
        this.accountIds = (accountIds == null) ? new ArrayList<>() : new ArrayList<>(accountIds);
    }

    public void setRole(Role role)
    {
        this.role = role;
    }

    // only called when a caller actually supplied a new password. omitting one on PUT must
    // leave the stored value alone, so the guard lives at the call site rather than here.
    public void setPassword(String password)
    {
        this.password = password;
    }

    //no setter for id, hard coded at seed
}