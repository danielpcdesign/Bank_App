package com.bank.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "customers")
public class Customer
{
    @Id
    private Integer id;
    private String username;
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