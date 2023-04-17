package com.rajeev.inventoryservice.repository;

import com.rajeev.inventoryservice.model.Inventory;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long>{
    List<Inventory> findBySkuCodeIn(List<String> skuCode);
}