package com.bank.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.bank.model.Customer;

// same five methods as before. the storage behind them is now atlas, not a map.
// nothing else changes
@Repository
public class CustomerRepository
{
    private final CustomerMongoRepository mongo;

    public CustomerRepository(CustomerMongoRepository mongo)
    {
        this.mongo = mongo;
        seedIfEmpty();
    }

    // only write the three seed customers when the collection is empty.
    private void seedIfEmpty()
    {
        if (mongo.count() == 0)
        {
            mongo.save(new Customer(1, "alice", "Alice Smith"));
            mongo.save(new Customer(2, "bob", "Bob Jones"));
            mongo.save(new Customer(3, "carol", "Carol Johnson"));
        }
    }

    // findAll already builds a fresh list, so no defensive copy is needed here
    public List<Customer> getCustomers()
    {
        return mongo.findAll();
    }

    // int autoboxes to the Integer the interface was declared with
    public Optional<Customer> findById(int id)
    {
        return mongo.findById(id);
    }

    //catch duplicate and return empty optional, otherwise return the saved customer in an optional.
    public Optional<Customer> addCustomer(Customer customer)
    {
        Optional<Customer> result; 
        try
        {
            result = Optional.of(mongo.insert(customer));
        }
        catch (DuplicateKeyException e)
        {
            System.out.println("customer already exists, not adding: " + customer.getId());
            return Optional.empty();
        }
        return result;
    }

    // deleteById returns void, so we have to check for existence first to know if it was deleted or not.
    public boolean deleteById(int id)
    {
        if (!mongo.existsById(id))
        {
            return false;
        }

        mongo.deleteById(id);
        return true;
    }

    // empty Optional when no document has that id.
    // otherwise build the replacement from data using path as key
    public Optional<Customer> editCustomer(int id, Customer data)
    {
        if(!mongo.existsById(data.getId()))
        {
            return Optional.empty();
        }
        return Optional.of(mongo.save(new Customer(id, data.getUsername(), data.getFullName())));
    }
}
