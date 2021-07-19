package com.ds.datastore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DatastoreApplication {

	private static final Logger LOGGER= LoggerFactory.getLogger(DatastoreApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(DatastoreApplication.class, args);
	}

}
