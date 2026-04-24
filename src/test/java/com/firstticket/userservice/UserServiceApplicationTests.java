package com.firstticket.userservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")  // application-test.yaml 활성화
class UserServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
