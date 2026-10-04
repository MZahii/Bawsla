/**
 * Clients OpenFeign vers les autres microservices (appel par nom Eureka, ex. "user-service").
 * Exemple de référence : quiz-service, package client (CoursClient + FeignHeadersInterceptor).
 * Ne jamais accéder à la base d'un autre service : passer par son API documentée dans CONTRATS_API.md.
 */
package tn.esprit.bawsla.cours.client;
