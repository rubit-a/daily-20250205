package com.example.databaseoptimization.repository

import com.example.databaseoptimization.data.entity.UserJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface UserRepository : JpaRepository<UserJpaEntity, Long> {

    fun findByEmail(email: String): Optional<UserJpaEntity>
}
