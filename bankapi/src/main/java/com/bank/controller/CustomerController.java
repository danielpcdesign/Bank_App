package com.bank.controller;
import java.net.URI;
import java.util.List;

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

// translates http to java and back. no business rules live here
@RestController
@RequestMapping("/api/v1")
public class CustomerController
{

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) //inject customerService dependency
    {
        this.customerService = customerService;
    }

    // GET http://localhost:8080/api/v1/customers
    @GetMapping("/customers")
    public List<Customer> getAllCustomers()
    {
        return customerService.getAllCustomers();
    }

    @GetMapping("/customers/{id}")
    public ResponseEntity<Customer> getCustomerById(@PathVariable int id)
    {   
        return customerService.getCustomerById(id)
            .map(ResponseEntity::ok) //200
            .orElseGet(() -> ResponseEntity.notFound().build()); //404
    }

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

    @PostMapping("/customers")
    public ResponseEntity<Customer> addCustomer(@RequestBody Customer customer)
    {
        if (customer.getId() == null)
        {
            return ResponseEntity.badRequest().build(); //400
        }

        if(!customerService.addNewCustomer(customer))
        {
            return ResponseEntity.status(HttpStatus.CONFLICT).build(); //409 
        }
        return ResponseEntity.created(URI.create("/api/v1/customers/" + customer.getId())).body(customer); //201 created
    }

    @PutMapping("/customers/{id}")
    public ResponseEntity<Customer> editCustomer(@PathVariable int id, @RequestBody Customer customer)
    {
        if (customer.getId() == null || customer.getId() != id)
        {
            return ResponseEntity.badRequest().build(); //400 body is not valid or id mismatch
        }

        return customerService.editCustomer(id, customer)
            .map(ResponseEntity::ok) //200
            .orElseGet(() -> ResponseEntity.notFound().build()); //404
    }
}
