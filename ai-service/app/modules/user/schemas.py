from app.core.schemas import CamelModel


class RecommandationsRequest(CamelModel):
    etudiant_id: int


class RecommandationsResponse(CamelModel):
    etudiant_id: int
    profil: str
    cours_recommandes: list[int]
    stub: bool = True
