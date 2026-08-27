package com.bank.service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.bank.model.Customer;
import com.bank.repository.CustomerRepository;

@Service
public class CustomerService
{
    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository)
    {
        this.customerRepository = customerRepository;
    }

    public List<Customer> getAllCustomers()
    {
        return customerRepository.getCustomers();
    }

    public Optional<Customer> getCustomerById(int id)
    {
        return customerRepository.findById(id);
    }

    // unique _id. 
    // an empty Optional back
    // means the insert was rejected as a duplicate
    public boolean addNewCustomer(Customer customer)
    {
        return customerRepository.addCustomer(customer).isPresent();
    }

    public boolean deleteCustomerById(int id)
    {
        return customerRepository.deleteById(id);

    }

    public Optional<Customer> editCustomer(int id, Customer data)
    {
        // Objects.equals, not !=, so a null body id is a mismatch rather than an NPE.
        if (!Objects.equals(data.getId(), id))
        {
            return Optional.empty(); // id mismatch
        }

        // existence checked at repo level
        return customerRepository.editCustomer(id, data);
    }
}