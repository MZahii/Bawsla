from pydantic import Field

from app.core.schemas import CamelModel


class QuestionsSimilairesRequest(CamelModel):
    question: str = Field(min_length=1)
    cours_id: int | None = None
    limite: int = Field(default=5, ge=1, le=20)


class DiscussionSimilaire(CamelModel):
    discussion_id: int
    titre: str
    score: float


class QuestionsSimilairesResponse(CamelModel):
    question: str
    similaires: list[DiscussionSimilaire]
    stub: bool = True
