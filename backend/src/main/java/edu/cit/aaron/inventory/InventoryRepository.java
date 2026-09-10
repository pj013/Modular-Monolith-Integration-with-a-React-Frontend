package edu.cit.aaron.inventory;

import org.springframework.data.jpa.repository.JpaRepository;

interface InventoryRepository extends JpaRepository<InventoryEntity, String> {
}
