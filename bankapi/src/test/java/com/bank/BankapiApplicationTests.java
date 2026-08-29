package com.bank;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.bank.repository.AccountMongoRepository;
import com.bank.repository.CustomerMongoRepository;


@SpringBootTest(properties = "spring.mongodb.uri=mongodb://localhost:27017/bankdb-test") //override URI so its not dependent on MONGODB_URI
class BankapiApplicationTests {

	@MockitoBean
  	private CustomerMongoRepository mongo;

	// AccountRepository seeds from its constructor too, so without this the context tries to
	// reach a real mongo and the test hangs for 30s before failing.
	@MockitoBean
  	private AccountMongoRepository accountMongo;

	@Test
	void contextLoads() {
	}

}
