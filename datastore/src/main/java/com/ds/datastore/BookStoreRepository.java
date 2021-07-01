package com.ds.datastore;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface BookStoreRepository extends JpaRepository<BookStore, Long> {

    List<BookStore> findByServerId(Long serverID);
}