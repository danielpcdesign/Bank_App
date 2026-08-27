package com.bank;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.bank.repository.CustomerMongoRepository;


@SpringBootTest(properties = "spring.mongodb.uri=mongodb://localhost:27017/bankdb-test") //override URI so its not dependent on MONGODB_URI
class BankapiApplicationTests {

	@MockitoBean
  	private CustomerMongoRepository mongo;

	@Test
	void contextLoads() {
	}

}
