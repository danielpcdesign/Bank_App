package com.bank.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.bank.model.Account;

// no implementation on purpose. spring data writes one at startup from the interface itself.
// Integer is the type of the @Id field on Account
public interface AccountMongoRepository extends MongoRepository<Account, Integer>
{
}
