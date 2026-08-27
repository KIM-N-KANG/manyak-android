package app.manyak.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.manyak.core.ui.R

/** 기본 서체. UI 전반에 쓴다. */
val Pretendard =
    FontFamily(
        Font(R.font.pretendard_regular, FontWeight.Normal),
        Font(R.font.pretendard_medium, FontWeight.Medium),
        Font(R.font.pretendard_bold, FontWeight.Bold),
    )

/** 서사 서체. 스토리 본문([ManyakTypography.bodyReading]) 전용이며 UI 요소에 쓰지 않는다. */
val GowunBatang =
    FontFamily(
        Font(R.font.gowun_batang_regular, FontWeight.Normal),
        Font(R.font.gowun_batang_bold, FontWeight.Bold),
    )

/**
 * Semantic — 타이포 롤. 이름은 토큰 정본의 `typography.*`와 1:1로 대응한다.
 *
 * 행간은 배수가 아니라 sp 절대값이다. 배수로 두면 플랫폼마다 반올림이 갈린다.
 *
 * 굵기는 Regular·Medium·Bold만 쓴다. SemiBold(600)를 쓰지 않는 이유는 번들에 없는 굵기를 요구하면
 * Bold로 대체 렌더되어 의도보다 두꺼워지기 때문이다.
 *
 * [titleMediumStrong] 만 크기가 아니라 굵기로 갈리는 롤이다. 이름을 `titleSmall` 같은 크기 이름으로
 * 두지 않은 것은, 크기 이름이 굵기 차이를 뜻하게 되면 스케일이 거짓말을 하기 때문이다.
 */
@Immutable
data class ManyakTypography(
    val labelSmall: TextStyle,
    val labelLarge: TextStyle,
    val bodySmall: TextStyle,
    val bodyMedium: TextStyle,
    val bodyLarge: TextStyle,
    val bodyReading: TextStyle,
    val titleMedium: TextStyle,
    val titleMediumStrong: TextStyle,
    val titleLarge: TextStyle,
    val headlineSmall: TextStyle,
)

internal val ManyakDefaultTypography =
    ManyakTypography(
        // 타임스탬프·최소 보조 문구
        labelSmall =
            TextStyle(
                fontFamily = Pretendard,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            ),
        // 버튼·탭 라벨
        labelLarge =
            TextStyle(
                fontFamily = Pretendard,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            ),
        // 메타 정보·보조 설명
        bodySmall =
            TextStyle(
                fontFamily = Pretendard,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            ),
        // 본문 기본
        bodyMedium =
            TextStyle(
                fontFamily = Pretendard,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            ),
        // 강조 본문·입력 필드
        bodyLarge =
            TextStyle(
                fontFamily = Pretendard,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 24.sp,
            ),
        // 스토리 본문. 장문 전용이라 행간을 1.75로 벌리고 자간을 2% 좁힌다
        bodyReading =
            TextStyle(
                fontFamily = GowunBatang,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 28.sp,
                letterSpacing = (-0.02).em,
            ),
        // 섹션·카드 제목
        titleMedium =
            TextStyle(
                fontFamily = Pretendard,
                fontWeight = FontWeight.Medium,
                fontSize = 18.sp,
                lineHeight = 26.sp,
            ),
        // 목록 섹션 제목. titleMedium 과 크기·행간이 같고 굵기로만 갈린다
        titleMediumStrong =
            TextStyle(
                fontFamily = Pretendard,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                lineHeight = 26.sp,
            ),
        // 화면 제목
        titleLarge =
            TextStyle(
                fontFamily = Pretendard,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                lineHeight = 27.sp,
            ),
        // 온보딩·랜딩 헤드라인
        headlineSmall =
            TextStyle(
                fontFamily = Pretendard,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                lineHeight = 30.sp,
            ),
    )
