package com.bank.model;

// the two kinds of account phase 1 modelled as subclasses. one collection per model means
// one class, so what was the subclass is now a field - and getType() becomes this enum
// rather than the string each subclass returned.
public enum AccountType
{
    SAVINGS,
    CHECKING
}
