package com.dukaan.app.data.local.dao

import androidx.room.*
import com.dukaan.app.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ShopProfileDao {
    @Query("SELECT * FROM shop_profile ORDER BY updated_at DESC LIMIT 1")
    fun getShopProfile(): Flow<ShopProfileEntity?>

    @Query("SELECT * FROM shop_profile ORDER BY updated_at DESC LIMIT 1")
    suspend fun getShopProfileOnce(): ShopProfileEntity?

    @Query("DELETE FROM shop_profile")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(profile: ShopProfileEntity)
}

@Dao
interface ProductCategoryDao {
    @Query("SELECT * FROM product_categories ORDER BY name ASC")
    fun getAllCategories(): Flow<List<ProductCategoryEntity>>

    @Query("SELECT * FROM product_categories WHERE id = :id")
    suspend fun getCategoryById(id: String): ProductCategoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(category: ProductCategoryEntity)

    @Update
    suspend fun update(category: ProductCategoryEntity)

    @Delete
    suspend fun delete(category: ProductCategoryEntity)
}

@Dao
interface ProductDao {
    @Query("SELECT * FROM products WHERE is_active = 1 ORDER BY name ASC")
    fun getAllActiveProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products ORDER BY name ASC")
    fun getAllProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE id = :id")
    fun getProductById(id: String): Flow<ProductEntity?>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getProductByIdOnce(id: String): ProductEntity?

    @Query("SELECT * FROM products WHERE is_active = 1 AND (name LIKE '%' || :query || '%' OR barcode = :query OR sku LIKE '%' || :query || '%') ORDER BY name ASC")
    fun searchProducts(query: String): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE is_active = 1 AND current_stock > 0 AND current_stock <= min_stock_level ORDER BY current_stock ASC")
    fun getLowStockProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE is_active = 1 AND current_stock <= 0 ORDER BY name ASC")
    fun getOutOfStockProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE is_active = 1 AND category_id = :categoryId ORDER BY name ASC")
    fun getProductsByCategory(categoryId: String): Flow<List<ProductEntity>>

    @Query("SELECT COUNT(*) FROM products WHERE is_active = 1")
    fun getActiveProductCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM products")
    suspend fun getProductCountOnce(): Int

    @Query("SELECT SUM(current_stock * weighted_avg_cost_paise) FROM products WHERE is_active = 1")
    fun getTotalInventoryValuePaise(): Flow<Long?>

    @Query("SELECT COUNT(*) FROM products WHERE is_active = 1 AND current_stock > 0 AND current_stock <= min_stock_level")
    fun getLowStockCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM products WHERE is_active = 1 AND current_stock <= 0")
    fun getOutOfStockCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(product: ProductEntity)

    @Update
    suspend fun update(product: ProductEntity)

    @Query("UPDATE products SET current_stock = :newStock, weighted_avg_cost_paise = :newWeightedCost, updated_at = :now WHERE id = :productId")
    suspend fun updateStockAndCost(productId: String, newStock: Double, newWeightedCost: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE products SET current_stock = :newStock, updated_at = :now WHERE id = :productId")
    suspend fun updateStock(productId: String, newStock: Double, now: Long = System.currentTimeMillis())

    @Query("UPDATE products SET is_active = 0, updated_at = :now WHERE id = :productId")
    suspend fun archiveProduct(productId: String, now: Long = System.currentTimeMillis())
}