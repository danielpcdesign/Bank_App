package com.bank.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.bank.model.Customer;

// no implementation on purpose. spring data writes one at startup from the interface itself.
// Integer is the type of the @Id field on Customer
public interface CustomerMongoRepository extends MongoRepository<Customer, Integer>
{
}
