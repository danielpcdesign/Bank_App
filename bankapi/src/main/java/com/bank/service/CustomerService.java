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
        return customerRepository.findByID(id);
    }

    public void addNewCustomer(Customer customer)
    {
        customerRepository.addCustomer(customer);
    }

    public boolean deleteCustomerById(int id)
    {
        return customerRepository.deleteById(id);

    }
}