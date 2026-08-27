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
import org.springframework.web.bind.annotation.RestController;

import com.bank.model.Customer;
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
        description = "Returns every customer. No paging - the collection is small by design in this phase.")
    @ApiResponse(
        responseCode = "200",
        description = "The full list, possibly empty.",
        content = @Content(
            mediaType = "application/json",
            array = @ArraySchema(schema = @Schema(implementation = Customer.class))))
    @GetMapping("/customers")
    public List<Customer> getAllCustomers()
    {
        return customerService.getAllCustomers();
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
            content = @Content)
    })
    @DeleteMapping("/customers/{id}")
    public ResponseEntity<Void> deleteCustomerById(@PathVariable int id)
    {
        boolean response = customerService.deleteCustomerById(id);
        if (response)
        {
            return ResponseEntity.noContent().build(); //empty. 204
        }
        else   
        {
            return ResponseEntity.notFound().build(); //service not found. 404
        }
    }

    // springdoc infers the request and response *shape* from the signature, but it cannot
    // infer which statuses this method actually returns - ResponseEntity<Customer> says nothing
    // about 201 vs 409. left undeclared it publishes a single 200, which is not merely
    // incomplete, it is wrong. these annotations are the contract, stated.
    @Operation(
        summary = "Create a customer",
        description = "The client supplies the id; none is generated. A duplicate id is 409 rather than 400 "
                    + "because the failure is state-dependent - the same request succeeds once that id is freed.")
    @ApiResponses({
        @ApiResponse(
            responseCode = "201",
            description = "Created.",
            headers = @Header(
                name = "Location",
                description = "Path of the new resource, /api/v1/customers/{id}",
                schema = @Schema(type = "string")),
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = Customer.class))),
        @ApiResponse(
            responseCode = "400",
            description = "Body rejected: missing or non-positive id, or blank username / fullName. "
                        + "The response names no field, deliberately.",
            content = @Content),          // empty @Content = no body. without this springdoc
        @ApiResponse(                     // claims every error also returns a Customer.
            responseCode = "409",
            description = "That id is already taken.",
            content = @Content)
    })
    @PostMapping("/customers")
    public ResponseEntity<Customer> addCustomer(@Valid @RequestBody Customer customer)
    {
        if (customer.getId() == null)
        {
        return ResponseEntity.badRequest().build(); //400
        } //redundant but keeping it (@NotNull in model)
            
        if(!customerService.addNewCustomer(customer))
        {
            return ResponseEntity.status(HttpStatus.CONFLICT).build(); //409 
        }
        return ResponseEntity.created(URI.create("/api/v1/customers/" + customer.getId())).body(customer); //201 created
    }

    @Operation(
        summary = "Replace a customer",
        description = "Full replacement, not a partial update - every field in the body is written. The body's "
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
            content = @Content)
    })
    @PutMapping("/customers/{id}")
    public ResponseEntity<Customer> editCustomer(@PathVariable int id, @Valid @RequestBody Customer customer)
    {
        if (!Objects.equals(customer.getId(), id))
        {       
            return ResponseEntity.badRequest().build(); //mismatch between path and body. 400
        }

        return customerService.editCustomer(id, customer)
            .map(ResponseEntity::ok) //200
            .orElseGet(() -> ResponseEntity.notFound().build()); //404
    }
}
