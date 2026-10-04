from app.core.schemas import CamelModel


class ResumeRequest(CamelModel):
    cours_id: int
    texte: str | None = None


class ResumeResponse(CamelModel):
    cours_id: int
    resume: str
    stub: bool = True
