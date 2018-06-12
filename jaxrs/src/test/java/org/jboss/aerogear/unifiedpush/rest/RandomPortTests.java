package org.jboss.aerogear.unifiedpush.rest;

import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.context.embedded.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, classes = { WebConfigTest.class })
public class RandomPortTests {

	@LocalServerPort
    protected int localPort;

    @Test
    public void getPort() {
        assertThat("Should get a random port greater than zero!", localPort, not(8080));
    }

}
