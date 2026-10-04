from pydantic import Field

from app.core.schemas import CamelModel


class GenererQuizRequest(CamelModel):
    cours_id: int
    nb_questions: int = Field(default=5, ge=1, le=20)
    texte: str | None = None


class QuestionQcm(CamelModel):
    enonce: str
    choix: list[str]
    bonne_reponse: int
    notion: str | None = None


class GenererQuizResponse(CamelModel):
    cours_id: int
    questions: list[QuestionQcm]
    stub: bool = True
