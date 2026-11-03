package app.manyak.my.credit.domain

import app.manyak.common.domain.credit.AttendanceClaimer
import app.manyak.common.domain.credit.CreditTransactionReader

/** 이프 보상·사용. 잔액 자체는 프로필(`GET /auth/me`)이 정본이라 여기서 다시 들지 않는다. */
interface CreditRepository :
    AttendanceClaimer,
    CreditTransactionReader
