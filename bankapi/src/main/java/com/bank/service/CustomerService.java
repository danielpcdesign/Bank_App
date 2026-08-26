package com.bank.service;

import java.util.List;
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

    public boolean addNewCustomer(Customer customer)
    {
        if (customerRepository.findById(customer.getId()).isPresent())
        {
            return false; // customer with this id already exists
        }
        customerRepository.addCustomer(customer);
        return true;
    }

    public boolean deleteCustomerById(int id)
    {
        return customerRepository.deleteById(id);

    }

    public Optional<Customer> editCustomer(int id, Customer data)
    {
        if (data.getId() != id)
        {
            return Optional.empty(); // id mismatch
        }
        if (!customerRepository.findById(id).isPresent())
        {
            return Optional.empty(); // customer not found
        }
        return customerRepository.editCustomer(id, data);
    }
}