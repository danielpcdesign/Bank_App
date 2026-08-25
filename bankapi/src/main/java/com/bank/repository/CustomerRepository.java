package com.bank.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.bank.model.Customer;

// stands in for the database
// written as if the data were already remote, so the mongo swap touches only this file
@Repository
public class CustomerRepository
{
    private final List<Customer> customers = new ArrayList<>();

    // ids are assigned by hand, no generation. see AGENTS.md section 7
    public CustomerRepository()
    {
        customers.add(new Customer(1, "daniel", "Daniel Palencia"));
        customers.add(new Customer(2, "emily", "Emily Romero"));
        customers.add(new Customer(3, "marcus", "Marcus Webb"));
    }

    // returns the live list, not a copy. mutation is allowed only through this class
    public List<Customer> getCustomers()
    {
        return customers;
    }

    public Optional<Customer> findByID(int id)
    {
        return customers.stream().filter(c -> c.getId() == id).findFirst();
    }

    public void addCustomer(Customer customer)
    {
        customers.add(customer);
    }

    public boolean deleteById(int id)
    {
        return customers.removeIf(c -> c.getId() == id);
    }
}
