package com.bank.controller;
import java.net.URI;
import java.util.List;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bank.model.Account;
import com.bank.service.AccountService;
import com.bank.service.CustomerService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

// translates http to java and back. no business rules live here
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Accounts", description = "Bank accounts. Ids are assigned by the client. Amounts are whole numbers.")
public class AccountController
{

    private final AccountService accountService;
    private final CustomerService customerService;

    // CustomerService is here only to tell "no such customer" apart from "that account id is
    // taken" on the nested create. both are failures the account service reports as false,
    // and they are different status codes.
    public AccountController(AccountService accountService, CustomerService customerService)
    {
        this.accountService = accountService;
        this.customerService = customerService;
    }

    @Operation(
        summary = "List all accounts",
        description = "Returns every account. No paging - the collection is small by design in this phase.")
    @ApiResponse(
        responseCode = "200",
        description = "The full list, possibly empty.",
        content = @Content(
            mediaType = "application/json",
            array = @ArraySchema(schema = @Schema(implementation = Account.class))))
    @GetMapping("/accounts")
    public List<Account> getAllAccounts()
    {
        return accountService.getAllAccounts();
    }

    @Operation(
        summary = "Fetch one account by id",
        description = "Absence is a 404. The service reports it as an empty Optional; turning that into a "
                    + "status code is a decision only this layer makes.")
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Found.",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = Account.class))),
        @ApiResponse(
            responseCode = "404",
            description = "No account has that id.",
            content = @Content)
    })
    @GetMapping("/accounts/{id}")
    public ResponseEntity<Account> getAccountById(@PathVariable int id)
    {
        return accountService.getAccountById(id)
            .map(ResponseEntity::ok) //200
            .orElseGet(() -> ResponseEntity.notFound().build()); //404
    }

    @Operation(
        summary = "Create an account for a customer",
        description = "The owning customer is REQUIRED, as ?customerId=. An account owned by nobody is not a "
                    + "meaningful record in this domain - it would appear in no customer's accountIds and be "
                    + "reachable only by listing every account - so it is made unrepresentable rather than "
                    + "merely discouraged. The client supplies the account id; none is generated. Identical in "
                    + "behaviour to POST /customers/{customerId}/accounts, which it delegates to - the two are "
                    + "the same operation addressed two ways, not two implementations to keep in step.")
    @ApiResponses({
        @ApiResponse(
            responseCode = "201",
            description = "Created.",
            headers = @Header(
                name = "Location",
                description = "Path of the new resource, /api/v1/accounts/{id}",
                schema = @Schema(type = "string")),
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = Account.class))),
        @ApiResponse(
            responseCode = "400",
            description = "Body rejected: missing or non-positive id, or missing type. Or customerId absent "
                        + "from the query. The response names no field, deliberately.",
            content = @Content),
        @ApiResponse(
            responseCode = "404",
            description = "No customer has that id.",
            content = @Content),
        @ApiResponse(
            responseCode = "409",
            description = "That account id is already taken.",
            content = @Content)
    })
    @PostMapping("/accounts")
    public ResponseEntity<Account> addAccount(@RequestParam int customerId, @Valid @RequestBody Account account)
    {
        // delegates rather than duplicating: same guards, same order, same status codes.
        // if the ownership rule ever changes it changes in exactly one place.
        return openAccountForCustomer(customerId, account);
    }

    @Operation(
        summary = "Replace an account",
        description = "Full replacement, not a partial update. The body's id must equal the path's. Note this "
                    + "writes the balance directly and is the one route that moves money without going through "
                    + "the account's own rules - deposit and withdraw are the guarded path.")
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Replaced. Returns the stored record.",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = Account.class))),
        @ApiResponse(
            responseCode = "400",
            description = "Either the body failed validation, or its id does not match the path id. "
                        + "The response distinguishes neither, deliberately.",
            content = @Content),
        @ApiResponse(
            responseCode = "404",
            description = "No account has that id. PUT does not create.",
            content = @Content)
    })
    @PutMapping("/accounts/{id}")
    public ResponseEntity<Account> editAccount(@PathVariable int id, @Valid @RequestBody Account account)
    {
        if (!Objects.equals(account.getId(), id))
        {
            return ResponseEntity.badRequest().build(); //mismatch between path and body. 400
        }

        return accountService.editAccount(id, account)
            .map(ResponseEntity::ok) //200
            .orElseGet(() -> ResponseEntity.notFound().build()); //404
    }

    @Operation(
        summary = "Delete an account",
        description = "204 on success with no body. Also removes the account's id from whichever customer "
                    + "listed it, so no customer is left pointing at a document that no longer exists.")
    @ApiResponses({
        @ApiResponse(
            responseCode = "204",
            description = "Deleted. No body.",
            content = @Content),
        @ApiResponse(
            responseCode = "404",
            description = "No account has that id.",
            content = @Content)
    })
    @DeleteMapping("/accounts/{id}")
    public ResponseEntity<Void> deleteAccountById(@PathVariable int id)
    {
        boolean response = accountService.deleteAccountById(id);
        if (response)
        {
            return ResponseEntity.noContent().build(); //204
        }
        else
        {
            return ResponseEntity.notFound().build(); //404
        }
    }

    //----------------------------------------------------------------CUSTOMER SCOPED----------------------------------------------------------------

    @Operation(
        summary = "List a customer's accounts",
        description = "A customer with no accounts is 200 and an empty list. Only an absent customer is 404 - "
                    + "the two are different answers and the status codes keep them apart.")
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "The customer's accounts, possibly empty.",
            content = @Content(
                mediaType = "application/json",
                array = @ArraySchema(schema = @Schema(implementation = Account.class)))),
        @ApiResponse(
            responseCode = "404",
            description = "No customer has that id.",
            content = @Content)
    })
    @GetMapping("/customers/{customerId}/accounts")
    public ResponseEntity<List<Account>> getAccountsForCustomer(@PathVariable int customerId)
    {
        return accountService.getAccountsForCustomer(customerId)
            .map(ResponseEntity::ok) //200
            .orElseGet(() -> ResponseEntity.notFound().build()); //404
    }

    @Operation(
        summary = "Open an account for a customer",
        description = "Creates the account and adds its id to the customer's list in one call. 404 when the "
                    + "customer does not exist, 409 when the account id is taken - established in that order, "
                    + "because a create against a missing customer is not a conflict.")
    @ApiResponses({
        @ApiResponse(
            responseCode = "201",
            description = "Opened.",
            headers = @Header(
                name = "Location",
                description = "Path of the new resource, /api/v1/accounts/{id}",
                schema = @Schema(type = "string")),
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = Account.class))),
        @ApiResponse(
            responseCode = "400",
            description = "Body rejected: missing or non-positive id, or missing type.",
            content = @Content),
        @ApiResponse(
            responseCode = "404",
            description = "No customer has that id.",
            content = @Content),
        @ApiResponse(
            responseCode = "409",
            description = "That account id is already taken.",
            content = @Content)
    })
    @PostMapping("/customers/{customerId}/accounts")
    public ResponseEntity<Account> openAccountForCustomer(@PathVariable int customerId, @Valid @RequestBody Account account)
    {
        if (account.getId() == null)
        {
            return ResponseEntity.badRequest().build(); //400
        }

        // asked first so a missing customer is a 404 rather than being folded into the
        // service's single false, which the next line reads as a conflict.
        if (customerService.getCustomerById(customerId).isEmpty())
        {
            return ResponseEntity.notFound().build(); //404
        }

        if (!accountService.openAccountForCustomer(customerId, account))
        {
            return ResponseEntity.status(HttpStatus.CONFLICT).build(); //409
        }
        return ResponseEntity.created(URI.create("/api/v1/accounts/" + account.getId())).body(account); //201
    }

    @Operation(
        summary = "Close a customer's account",
        description = "Deletes the account and unlinks it. 404 covers both an absent customer and an account "
                    + "this customer does not own - from the caller's side the addressed resource does not "
                    + "exist either way, and separating them would report on records the caller cannot see.")
    @ApiResponses({
        @ApiResponse(
            responseCode = "204",
            description = "Closed. No body.",
            content = @Content),
        @ApiResponse(
            responseCode = "404",
            description = "No such customer, or that customer does not own that account.",
            content = @Content)
    })
    @DeleteMapping("/customers/{customerId}/accounts/{accountId}")
    public ResponseEntity<Void> closeAccountForCustomer(@PathVariable int customerId, @PathVariable int accountId)
    {
        boolean response = accountService.closeAccountForCustomer(customerId, accountId);
        if (response)
        {
            return ResponseEntity.noContent().build(); //204
        }
        else
        {
            return ResponseEntity.notFound().build(); //404
        }
    }

    //----------------------------------------------------------------OPERATIONS----------------------------------------------------------------

    @Operation(
        summary = "Deposit into an account",
        description = "Amount is a whole number greater than zero. A malformed amount is 400 because no state "
                    + "would ever make it succeed; a refusal by the account's own rules is 409 because it is "
                    + "state-dependent. Deposit only ever fails on the amount, so its 409 is unreachable in practice.")
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Deposited. Returns the account as stored.",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = Account.class))),
        @ApiResponse(
            responseCode = "400",
            description = "Amount is zero, negative, or not a whole number.",
            content = @Content),
        @ApiResponse(
            responseCode = "404",
            description = "No account has that id.",
            content = @Content),
        @ApiResponse(
            responseCode = "409",
            description = "The account's rules refused the deposit.",
            content = @Content)
    })
    @PostMapping("/accounts/{id}/deposit")
    public ResponseEntity<Account> deposit(@PathVariable int id, @RequestParam double amount)
    {
        if (!isWholeAndPositive(amount))
        {
            return ResponseEntity.badRequest().build(); //400
        }

        if (accountService.getAccountById(id).isEmpty())
        {
            return ResponseEntity.notFound().build(); //404
        }

        return accountService.deposit(id, amount)
            .map(ResponseEntity::ok) //200
            .orElseGet(() -> ResponseEntity.status(HttpStatus.CONFLICT).build()); //409
    }

    @Operation(
        summary = "Withdraw from an account",
        description = "Amount is a whole number greater than zero. Insufficient funds is 409, not 400: the same "
                    + "request succeeds once the balance allows it. Savings refuses to go below zero; checking "
                    + "refuses to go below its overdraft limit.")
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Withdrawn. Returns the account as stored.",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = Account.class))),
        @ApiResponse(
            responseCode = "400",
            description = "Amount is zero, negative, or not a whole number.",
            content = @Content),
        @ApiResponse(
            responseCode = "404",
            description = "No account has that id.",
            content = @Content),
        @ApiResponse(
            responseCode = "409",
            description = "The withdrawal would take the balance past the account's floor.",
            content = @Content)
    })
    @PostMapping("/accounts/{id}/withdraw")
    public ResponseEntity<Account> withdraw(@PathVariable int id, @RequestParam double amount)
    {
        if (!isWholeAndPositive(amount))
        {
            return ResponseEntity.badRequest().build(); //400
        }

        if (accountService.getAccountById(id).isEmpty())
        {
            return ResponseEntity.notFound().build(); //404
        }

        return accountService.withdraw(id, amount)
            .map(ResponseEntity::ok) //200
            .orElseGet(() -> ResponseEntity.status(HttpStatus.CONFLICT).build()); //409
    }

    // the model enforces this too, and must - there it is the invariant. this copy is not
    // the invariant, it is the 400/409 split: an amount no state could ever accept is a bad
    // request, and only a refusal that depends on the balance is a conflict.
    private static boolean isWholeAndPositive(double amount)
    {
        return Double.isFinite(amount) && amount > 0 && amount == Math.rint(amount);
    }
}
