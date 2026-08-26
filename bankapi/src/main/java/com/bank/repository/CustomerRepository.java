package com.bank.repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.bank.model.Customer;

// stands in for the database
@Repository
public class CustomerRepository
{
    // keyed by id, the way every lookup here asks for it. linked to keep seed order 
    private final Map<Integer, Customer> customers = new LinkedHashMap<>();

    // ids are assigned by hand, no generation
    public CustomerRepository()
    {
        seed(new Customer(1, "daniel", "Daniel Palencia"));
        seed(new Customer(2, "emily", "Emily Romero"));
        seed(new Customer(3, "marcus", "Marcus Webb"));
    }

    private void seed(Customer customer)
    {
        customers.put(customer.getId(), customer);
    }

    // fresh list per call
    public List<Customer> getCustomers()
    {
        return new ArrayList<>(customers.values());
    }

    public Optional<Customer> findById(int id)
    {
        return Optional.ofNullable(customers.get(id));
    }

    public void addCustomer(Customer customer)
    {
        customers.put(customer.getId(), customer);
    }

    // remove returns the old value, or null when the key was absent
    public boolean deleteById(int id)
    {
        return customers.remove(id) != null;
    }

    // full replacement. scales better if another field is added. 
    public Optional<Customer> editCustomer(int id, Customer data)
    {
        if (!customers.containsKey(id))
        {
            return Optional.empty();
        }

        customers.put(id, data); // replace the old customer with the new one
        return Optional.of(data);
    }
}
