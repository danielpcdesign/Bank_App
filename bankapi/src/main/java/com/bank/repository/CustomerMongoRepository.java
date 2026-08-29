package com.bank.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

import com.bank.model.Customer;
import com.bank.model.Role;

// no implementation on purpose. spring data writes one at startup from the interface itself.
// Integer is the type of the @Id field on Customer
public interface CustomerMongoRepository extends MongoRepository<Customer, Integer>
{
    // spring data derives the query from the method name - findBy + the field. no @Query,
    // no implementation, same as the five it already writes.
    List<Customer> findByRole(Role role);

    Optional<Customer> findByUsername(String username);
}
