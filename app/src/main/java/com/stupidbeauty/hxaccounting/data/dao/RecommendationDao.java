package com.stupidbeauty.hxaccounting.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.*;
import com.stupidbeauty.hxaccounting.data.entity.Recommendation;
import java.util.List;

/**
 * 推荐记录数据访问接口
 * 商业化预留 - CPS佣金追踪
 */
@Dao
public interface RecommendationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(Recommendation recommendation);

    @Update
    void update(Recommendation recommendation);

    @Delete
    void delete(Recommendation recommendation);

    // --- 按分类查询推荐记录 ---

    @Query("SELECT * FROM recommendations WHERE category = :category " +
           "ORDER BY clicked_at DESC LIMIT :limit")
    LiveData<List<Recommendation>> getByCategory(String category, int limit);

    // --- 按关联流水查询 ---

    @Query("SELECT * FROM recommendations WHERE transaction_id = :transactionId")
    LiveData<Recommendation> getByTransactionId(long transactionId);

    // --- 统计已购买数（用于评估转化率）---

    @Query("SELECT COUNT(*) FROM recommendations WHERE purchased_at IS NOT NULL " +
           "AND category = :category")
    LiveData<Integer> getPurchasedCount(String category);

    // --- 统计预估佣金总额 ---

    @Query("SELECT SUM(estimated_commission) FROM recommendations WHERE category = :category")
    LiveData<Double> getTotalEstimatedCommission(String category);

    // --- 标记为已购买（用户上报）---

    @Query("UPDATE recommendations SET purchased_at = :purchasedAt, " +
           "actual_commission = :actualCommission WHERE id = :id")
    void markPurchased(long id, long purchasedAt, Double actualCommission);
}
