package tn.esprit.bawsla.forum.entity;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Stocke un vecteur d'embedding en BLOB : float32 petit-boutiste, 4 octets par dimension
 * (768 dimensions = 3 Ko). La similarité cosinus est calculée en Java sur ces vecteurs normalisés.
 */
@Converter
public class VecteurConverter implements AttributeConverter<float[], byte[]> {

    @Override
    public byte[] convertToDatabaseColumn(float[] vecteur) {
        if (vecteur == null) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.allocate(vecteur.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        buffer.asFloatBuffer().put(vecteur);
        return buffer.array();
    }

    @Override
    public float[] convertToEntityAttribute(byte[] octets) {
        if (octets == null) {
            return null;
        }
        float[] vecteur = new float[octets.length / Float.BYTES];
        ByteBuffer.wrap(octets).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(vecteur);
        return vecteur;
    }
}
