package com.straycat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.straycat.entity.CoinTransaction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CoinTransactionMapper extends BaseMapper<CoinTransaction> {

    @Select("SELECT COALESCE(SUM(-amount), 0) FROM coin_transaction WHERE user_id = #{userId} AND biz_type = 'TIP'")
    int sumTipped(@Param("userId") Long userId);
}
