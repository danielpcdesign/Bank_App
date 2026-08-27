package com.bank.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Document(collection = "customers")
public class Customer
{
    @Id
    @NotNull
    @Positive
    private Integer id;

    @NotBlank
    private String username;
    
    @NotBlank
    private String fullName;

    public Customer()
    {

    }

    public Customer(Integer id, String username, String fullName)
    {
        this.id = id;
        this.username = username;
        this.fullName = fullName;
    }

    //----------------------------------------------------------------GETTERS----------------------------------------------------------------

    public Integer getId()
    {
        return id;
    }

    public String getUsername()
    {
        return username;
    }

    public String getFullName()
    {
        return fullName;
    }

    //----------------------------------------------------------------SETTERS----------------------------------------------------------------

    public void setFullName(String fullName)
    {
        this.fullName = fullName;
    }

    public void setUsername(String username)
    {
        this.username = username;
    }

    //no setter for id, hard coded at seed
}