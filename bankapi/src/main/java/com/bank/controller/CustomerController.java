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

import com.bank.model.Customer;
import com.bank.model.Role;
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
@Tag(name = "Customers", description = "Customer records. Ids are assigned by the client, not generated.")
public class CustomerController
{

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) //inject customerService dependency
    {
        this.customerService = customerService;
    }

    // GET http://localhost:8080/api/v1/customers
    // the only handler whose inferred 200 is already correct, so nothing is overridden here
    @Operation(
        summary = "List all customers",
        description = "Returns every customer, or only those in one role when ?role= is supplied. No paging - "
                    + "the collection is small by design in this phase. The role parameter FILTERS; it grants "
                    + "nothing. There is no authentication here, so nothing could be granted on the strength of "
                    + "a value the caller typed.")
    @ApiResponse(
        responseCode = "200",
        description = "The full list, possibly empty.",
        content = @Content(
            mediaType = "application/json",
            array = @ArraySchema(schema = @Schema(implementation = Customer.class))))
    @GetMapping("/customers")
    public List<Customer> getAllCustomers(@RequestParam(required = false) Role role)
    {
        if (role == null)
        {
            return customerService.getAllCustomers();
        }
        return customerService.getCustomersByRole(role);
    }

    @Operation(
        summary = "Fetch one customer by id",
        description = "Absence is a 404. The service reports it as an empty Optional; turning that into a "
                    + "status code is a decision only this layer makes.")
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Found.",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = Customer.class))),
        @ApiResponse(
            responseCode = "400",
            description = "The id in the path is not an integer. Produced by @PathVariable type conversion "
                        + "before this method runs.",
            content = @Content),
        @ApiResponse(
            responseCode = "404",
            description = "No customer has that id.",
            content = @Content)
    })
    @GetMapping("/customers/{id}")
    public ResponseEntity<Customer> getCustomerById(@PathVariable int id)
    {   
        return customerService.getCustomerById(id)
            .map(ResponseEntity::ok) //200
            .orElseGet(() -> ResponseEntity.notFound().build()); //404
    }

    // the one place the inferred spec was actively wrong, not just thin: ResponseEntity<Void>
    // publishes 200, and this method never returns 200.
    @Operation(
        summary = "Delete a customer",
        description = "204 on success with no body. Not idempotent in its reporting - a second delete of the "
                    + "same id is a 404, because the repository answers whether the record was there to remove.")
    @ApiResponses({
        @ApiResponse(
            responseCode = "204",
            description = "Deleted. No body.",
            content = @Content),
        @ApiResponse(
            responseCode = "400",
            description = "The id in the path is not an integer.",
            content = @Content),
        @ApiResponse(
            responseCode = "404",
            description = "No customer has that id.",
            content = @Content),
        @ApiResponse(
            responseCode = "409",
            description = "That customer is the last remaining admin. Refused for EVERY caller, curl "
                        + "included - this is an invariant about the state of the system rather than a "
                        + "rule about who is asking, so it needs no authentication to enforce. "
                        + "State-dependent: the same request succeeds once another admin exists.",
            content = @Content)
    })
    @DeleteMapping("/customers/{id}")
    public ResponseEntity<Void> deleteCustomerById(@PathVariable int id)
    {
        // existence is established here so that the service's false can mean one thing:
        // refused. same shape as deposit and withdraw.
        if (customerService.getCustomerById(id).isEmpty())
        {
            return ResponseEntity.notFound().build(); //404
        }

        if (!customerService.deleteCustomerById(id))
        {
            return ResponseEntity.status(HttpStatus.CONFLICT).build(); //409 - last admin
        }
        return ResponseEntity.noContent().build(); //204
    }

    // springdoc infers the request and response *shape* from the signature, but it cannot
    // infer which statuses this method actually returns - ResponseEntity<Customer> says nothing
    // about 201 vs 409. left undeclared it publishes a single 200, which is not merely
    // incomplete, it is wrong. these annotations are the contract, stated.
    @Operation(
        summary = "Create a customer",
        description = "The ONLY way a customer comes into existence through this API. The body carries "
                    + "username, password and fullName only - there is NO field for an id or a role, so no "
                    + "caller can choose either. The server assigns the next free id and always CUSTOMER. "
                    + "That is structural rather than a check: nothing strips a role, because a role cannot "
                    + "arrive. NOTE THE LIMIT - this closes the CREATE path only. PUT /api/v1/customers/{id} "
                    + "still accepts a role from any caller by an explicit decision, so a caller can create a "
                    + "customer here and then PUT itself to ADMIN. This briefly had a sibling at "
                    + "/customers/register; the two were merged and this is the survivor.")
    @ApiResponses({
        @ApiResponse(
            responseCode = "201",
            description = "Created. Returns the new customer, with its server-assigned id and without the password.",
            headers = @Header(
                name = "Location",
                description = "Path of the new resource, /api/v1/customers/{id}",
                schema = @Schema(type = "string")),
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = Customer.class))),
        @ApiResponse(
            responseCode = "400",
            description = "username, password or fullName missing or blank. "
                        + "The response names no field, deliberately.",
            content = @Content),          // empty @Content = no body. without this springdoc
        @ApiResponse(                     // claims every error also returns a Customer.
            responseCode = "409",
            description = "That username is taken. A real conflict - the caller cannot pick an id, so the "
                        + "username is the only thing two creates can collide on.",
            content = @Content)
    })
    @PostMapping("/customers")
    public ResponseEntity<Customer> addCustomer(@Valid @RequestBody CreateCustomerRequest request)
    {
        return customerService.addNewCustomer(request.username(), request.password(), request.fullName())
            .map(customer -> ResponseEntity
                .created(URI.create("/api/v1/customers/" + customer.getId()))
                .body(customer)) //201
            .orElseGet(() -> ResponseEntity.status(HttpStatus.CONFLICT).build()); //409
    }

    @Operation(
        summary = "Sign in",
        description = "Compares a username and password against the stored pair and returns that customer, so "
                    + "the caller can see which role they hold. This is NOT authentication: no session is "
                    + "created and no token is issued, so the next request is as anonymous as this one. Every "
                    + "other endpoint remains reachable without signing in at all. The response never contains "
                    + "the password. A wrong password and an unknown username return the same 401 - telling "
                    + "them apart would let a caller enumerate valid usernames.")
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Credentials matched. Returns the customer, without the password.",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = Customer.class))),
        @ApiResponse(
            responseCode = "400",
            description = "Username or password missing from the body.",
            content = @Content),
        @ApiResponse(
            responseCode = "401",
            description = "No such username, or the password did not match. Deliberately one answer for both.",
            content = @Content)
    })
    @PostMapping("/customers/signin")
    public ResponseEntity<Customer> signIn(@Valid @RequestBody SignInRequest request)
    {
        return customerService.signIn(request.username(), request.password())
            .map(ResponseEntity::ok) //200
            .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()); //401
    }

    @Operation(
        summary = "Replace a customer",
        description = "Full replacement, not a partial update - every field in the body is written. Note the "
                    + "body's role is written too, and NOTHING CHECKS WHO IS ASKING: any caller may promote "
                    + "anyone to ADMIN here. The admin-only restriction on that lives in the front end alone "
                    + "and is bypassed by curl or any other client. Documented rather than fixed - real "
                    + "enforcement needs an authenticated principal, in phase 10. The body's "
                    + "id must equal the path's. A mismatch is rejected rather than resolved: either half could "
                    + "be the typo, and guessing wrong overwrites the wrong record.")
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Replaced. Returns the stored record.",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = Customer.class))),
        @ApiResponse(
            responseCode = "400",
            description = "Either the body failed validation, or its id does not match the path id. "
                        + "The response distinguishes neither, deliberately.",
            content = @Content),
        @ApiResponse(
            responseCode = "404",
            description = "No customer has that id. PUT does not create.",
            content = @Content),
        @ApiResponse(
            responseCode = "409",
            description = "The body would demote the last remaining admin. Same invariant as DELETE and "
                        + "enforced for every caller: demoting the last admin strands the system exactly "
                        + "as deleting them would. Changing anything else about that customer is allowed.",
            content = @Content)
    })
    @PutMapping("/customers/{id}")
    public ResponseEntity<Customer> editCustomer(@PathVariable int id, @Valid @RequestBody Customer customer)
    {
        if (!Objects.equals(customer.getId(), id))
        {       
            return ResponseEntity.badRequest().build(); //mismatch between path and body. 400
        }

        // as with delete: existence first, so an empty Optional below means refused.
        if (customerService.getCustomerById(id).isEmpty())
        {
            return ResponseEntity.notFound().build(); //404
        }

        return customerService.editCustomer(id, customer)
            .map(ResponseEntity::ok) //200
            .orElseGet(() -> ResponseEntity.status(HttpStatus.CONFLICT).build()); //409 - last admin demotion
    }
}
