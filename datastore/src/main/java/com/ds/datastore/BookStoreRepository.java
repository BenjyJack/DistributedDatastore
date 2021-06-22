package com.ds.datastore;

import org.springframework.data.jpa.repository.JpaRepository;

interface BookStoreRepository extends JpaRepository<BookStore, Long> {

}