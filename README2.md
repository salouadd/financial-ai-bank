# Financial Coach Service — état du microservice et prochaines étapes

Ce document décrit ce qui existe actuellement dans le dépôt et propose un ordre concret pour terminer, tester et préparer le microservice à une intégration réelle. Il complète la documentation détaillée du projet ; il ne signifie pas que les éléments marqués « implémentés » ont déjà été validés en production.

## 1. Objectif du microservice

`financial-coach-service` est une API REST Java/Spring Boot destinée au coaching budgétaire. Elle permet de calculer une répartition de revenu, enregistrer un profil financier, produire des conseils assistés par IA, repérer un salaire récurrent, gérer des coffres d’épargne et enregistrer les consentements et opérations d’audit.

Le service identifie l’utilisateur avec le claim standard `sub` du JWT, qui doit contenir un UUID. Les données applicatives sont stockées dans PostgreSQL ; le service de transactions est appelé pour l’analyse de salaire. Un claim `userId` distinct ne doit pas servir à sélectionner un autre compte.

## 2. État actuel — ce qui est déjà fait

### Base technique

- Java 21, Spring Boot 3.5 et Maven Wrapper (`mvnw.cmd`).
- API REST organisée en `controller`, `service`, `repository`, `model` et `dto`.
- PostgreSQL avec Spring Data JPA et quatre migrations Flyway (`V1` à `V4`).
- Configuration du port HTTP `8082`, validation des entrées et gestion centralisée de certaines erreurs HTTP.
- Image Docker multi-étape : compilation Maven, image d’exécution Java, processus lancé avec un utilisateur non-root.
- Docker Compose pour démarrer PostgreSQL et l’application avec un volume persistant.
- Actuator configuré pour exposer `health`, `info` et `prometheus`.

### Fonctionnalités présentes dans le code

| Domaine | Fonction disponible |
|---|---|
| Profil budgétaire | Lire et créer/modifier le revenu mensuel net et les ratios du profil. Les ratios par défaut sont 50/30/20. |
| Répartition du budget | Calculer les enveloppes besoins/envies/épargne ; les ratios du profil ou des ratios personnalisés sont utilisés. |
| Analyse de dépenses par IA | Envoyer des dépenses agrégées par catégorie, nettoyer certaines entrées, filtrer les catégories et types de conseil, nettoyer les textes renvoyés par le modèle et limiter les conseils affichés. Un résultat de repli existe en cas d’échec de l’appel IA. |
| Historique des recommandations | Lire les recommandations paginées, les plus récentes en premier ; la taille de page est plafonnée à 50. Le contrôleur de lecture existe, mais le parcours d’analyse IA ne persiste pas encore les recommandations générées. |
| Détection de salaire | Récupérer les entrées de fonds sur les 90 derniers jours via le client du service de transactions, puis rechercher des versements créditeurs récurrents. |
| Coffres d’épargne | Créer, lister, renommer et supprimer un coffre. La suppression est refusée si son solde est positif. |
| Verrouillage d’un montant | Créer une intention de verrouillage expirant après cinq minutes, puis confirmer l’opération avec une clé d’idempotence. Une écriture de ledger et un audit sont enregistrés lors de la confirmation. |
| Confidentialité | Lire/modifier le consentement au traitement IA, exporter une partie des données du compte et demander l’effacement sous certaines conditions. |
| Audit et rétention | Plusieurs opérations enregistrent une entrée d’audit. Un scheduler quotidien supprime les recommandations expirées. |

### API actuellement déclarée

Tous les chemins ci-dessous sont préfixés par `/api/v1/coach` et requièrent un JWT avec le rôle `USER`, selon la configuration de sécurité actuelle.

| Méthode | Chemin | Usage |
|---|---|---|
| `GET` | `/profile` | Lire le profil budgétaire. |
| `PUT` | `/profile` | Créer ou modifier le profil budgétaire. |
| `POST` | `/allocate` | Calculer une allocation budgétaire. |
| `POST` | `/analyze-budget` | Analyser des dépenses agrégées avec l’IA. |
| `GET` | `/recommendations?page=0&size=10` | Consulter l’historique des recommandations. |
| `GET` | `/salary/detect` | Détecter un salaire récurrent à partir du service de transactions. |
| `POST` | `/vaults` | Créer un coffre. |
| `GET` | `/vaults` | Lister les coffres de l’utilisateur. |
| `PATCH` | `/vaults/{vaultId}` | Renommer un coffre. |
| `DELETE` | `/vaults/{vaultId}` | Supprimer un coffre vide. |
| `POST` | `/vaults/{vaultId}/lock-intent` | Créer une intention de verrouillage. |
| `POST` | `/vaults/{vaultId}/lock-confirm` | Confirmer l’intention. En-tête requis : `Idempotency-Key`. |
| `GET` | `/me/consent` | Lire le consentement. |
| `PUT` | `/me/consent` | Modifier le consentement. |
| `GET` | `/me/export` | Exporter les données actuellement incluses dans l’export. |
| `DELETE` | `/me/data` | Demander l’effacement des données actuellement prises en charge. |

## 3. Sécurité — feuille de route OWASP

Cette section rapproche le code actuel de l’**OWASP Top 10:2025** pour l’application web et de l’**OWASP Top 10 for LLM Applications:2025** pour les fonctions IA. C’est une liste d’actions à traiter, pas une certification ni un audit de sécurité complet.

### 3.1 Application web — OWASP Top 10:2025

| Catégorie OWASP | État observé dans ce dépôt | Mesures à réaliser |
|---|---|---|
| **A01 — Broken Access Control** | Les routes métier exigent le rôle `USER`; l’accès aux ressources est filtré par propriétaire `sub`. Les chemins `/actuator/**` exigent le rôle `ADMIN`. | Tester chaque route avec deux utilisateurs pour prévenir les accès IDOR, y compris coffres, recommandations, export, intentions et ledger. Confirmer avec le fournisseur d’identité que `sub` est un UUID stable. |
| **A02 — Security Misconfiguration** | Le profil local conserve des valeurs de développement. Le profil `prod` exige les paramètres critiques et refuse les identifiants PostgreSQL par défaut, des valeurs IA factices, les bases locales et les URLs HTTP pour JWKS, issuer, services de transactions et fournisseur SCA. | Utiliser un gestionnaire de secrets en déploiement; limiter les ports réseau, configurer TLS jusqu’à l’application et vérifier les paramètres effectifs ainsi que le durcissement de la base de données. |
| **A03 — Software Supply Chain Failures** | Le projet déclare ses dépendances Maven et utilise des images de base Java/Maven; aucun contrôle automatisé de dépendances n’est visible dans le dépôt. | Ajouter un contrôle de vulnérabilités des dépendances et images à la CI, mettre à jour les composants vulnérables, produire un SBOM et verrouiller/contrôler les versions et images de base. Examiner les alertes et définir une procédure de mise à jour. |
| **A04 — Cryptographic Failures** | La validation JWT utilise une URI JWKS; le Compose local transmet des paramètres de connexion, sans configuration de chiffrement applicatif spécifique visible ici. | Imposer HTTPS/TLS sur les communications externes et interservices en production, protéger et renouveler les secrets, chiffrer les sauvegardes et les données au repos selon les exigences bancaires. Ne jamais journaliser JWT, clé API, OTP ou données financières complètes. |
| **A05 — Injection** | Les accès aux données utilisent Spring Data JPA; les DTO bornent maintenant les montants, ratios, catégories et textes, les corps HTTP sont limités à 64 Kio et la pagination est validée. | Conserver les requêtes paramétrées, ne pas considérer le nettoyage par expressions régulières comme une protection générale, encoder les données selon leur contexte d’affichage dans le frontend et compléter les tests d’entrées malformées. |
| **A06 — Insecure Design** | Des règles de plafonnement, d’appartenance de coffre, d’idempotence et d’expiration existent. L’analyse IA exige un consentement actif. La SCA passe par un adaptateur HTTP configurable, échoue en mode fermé sans fournisseur, lie le défi à l’intention et limite les tentatives à cinq. | Sélectionner/approuver un fournisseur compatible et valider son API, la remise du code, son usage unique et l’expiration; finaliser aussi les règles de suppression/export et confirmer les conditions de rétention IA avant mise en production. |
| **A07 — Authentication Failures** | L’API est un resource server JWT et convertit le claim `role` en autorité Spring. La signature, l’émetteur (`iss`), l’audience (`aud`) et les dates du jeton sont validés. | Confirmer les valeurs d’identité réelles, les algorithmes autorisés, la rotation JWKS et la durée de vie des jetons avec le fournisseur; tester les jetons malformés et les rôles incorrects. |
| **A08 — Software or Data Integrity Failures** | Flyway versionne le schéma; les contraintes uniques empêchent le double ledger par clé utilisateur ou intention, les verrous transactionnels sérialisent les confirmations, et les clés réutilisées pour une autre opération donnent HTTP 409. La migration lie l’écriture ledger à la même intention, au coffre et au montant. | Protéger les migrations et artefacts par revue et CI; exécuter sur PostgreSQL les tests de courses concurrentes, migrations et échecs partiels. |
| **A09 — Security Logging and Alerting Failures** | Plusieurs opérations écrivent un audit; des logs d’application sont présents. L’audit et les métriques ne couvrent pas encore nécessairement tous les refus et appels aux dépendances. | Définir les événements de sécurité, la rétention et les accès aux journaux; alerter sur échecs répétés d’authentification/SCA, erreurs de dépendances et anomalies d’idempotence. Masquer les données personnelles et ne jamais enregistrer tokens, codes OTP, prompts complets ou secrets. |
| **A10 — Mishandling of Exceptional Conditions** | Le gestionnaire global formate les erreurs de validation; d’autres erreurs métier et intégration nécessitent une politique commune. L’appel IA utilise un résultat de repli en cas d’exception. | Retourner des erreurs stables sans stack trace ni détails internes, distinguer indisponibilité et résultat métier, ajouter délais/limites de reprise et tests d’échec. Rendre les replis observables (métrique/log sans données sensibles) afin qu’une panne ne ressemble pas silencieusement à une analyse normale. |

### 3.2 IA générative — OWASP Top 10 for LLM Applications:2025

| Risque LLM | État observé dans ce dépôt | Mesures à réaliser |
|---|---|---|
| **LLM01 — Prompt Injection** | Aucun texte libre fourni par l’utilisateur n’est désormais inséré dans le prompt; seules les catégories normalisées et les dépenses agrégées sont transmises, sans outil d’exécution déclaré. | Continuer à traiter les données comme non fiables; ne pas compter uniquement sur le prompt système. Conserver les limites d’entrée et tester les noms de catégories malveillants, les sorties et les changements de modèle. |
| **LLM02 — Sensitive Information Disclosure** | L’analyse exige un consentement actif; seuls les noms de catégories normalisés et leurs montants agrégés sont transmis. Revenu, objectif financier, identité, IP et historique détaillé ne sont pas inclus dans le prompt. Les conditions de rétention du fournisseur restent à confirmer. | Confirmer les régions, la rétention, l’usage et les conditions contractuelles du fournisseur IA avant la production. Ne jamais envoyer nom, identifiants, numéro de compte, JWT ou historique détaillé inutile. |
| **LLM03 — Supply Chain** | Spring AI et un fournisseur de modèle externe sont utilisés. | Évaluer les versions de bibliothèques, le fournisseur, les modèles et les changements de version. Enregistrer le modèle/version utilisés, vérifier les changements de comportement, prévoir un fournisseur/modèle de repli approuvé et ne pas envoyer de secrets dans les prompts. |
| **LLM04 — Data and Model Poisoning** | Il n’y a pas de pipeline d’entraînement ou de base vectorielle visible; les montants/catégories reçus de l’appelant alimentent le contexte de l’analyse. | Valider les catégories, montants, bornes et nombre d’éléments côté serveur. Si des données de feedback, de RAG ou de fine-tuning sont ajoutées, contrôler leur provenance, accès, intégrité et processus de validation avant utilisation. |
| **LLM05 — Improper Output Handling** | Types, catégories et montants sont validés; économies calculées depuis les seuls conseils retenus et plafonnées par catégorie. Les textes IA (résumé, conseils et abonnements) ne sont pas renvoyés : résumé et conseils sont générés par règles serveur. | Continuer à ne jamais exécuter une sortie IA comme HTML, SQL, commande ou action. Encoder selon le contexte d’affichage et conserver les limites lorsque le contrat du modèle évolue. |
| **LLM06 — Excessive Agency** | Le modèle génère des conseils et ne dispose pas actuellement d’outils lui permettant d’effectuer un transfert. Le verrouillage de coffre est une API distincte. | Préserver cette séparation : l’IA ne doit jamais déclencher directement une opération financière, modifier un profil ou contourner l’authentification/SCA. Toute future action doit exiger une API métier séparée, une autorisation explicite et une confirmation utilisateur. |
| **LLM07 — System Prompt Leakage** | Un prompt système fixe est envoyé au fournisseur. Aucun secret ne devrait y être placé. | Ne pas mettre de secret, règle d’autorisation ou donnée personnelle dans le prompt. Considérer le prompt comme découvrable; protéger les règles réellement sensibles dans le code serveur et tester les demandes d’extraction du prompt. |
| **LLM08 — Vector and Embedding Weaknesses** | Aucune recherche vectorielle, base d’embeddings ou fonction RAG n’est présente dans le code observé. | Non applicable au périmètre actuel. Si une fonction RAG est ajoutée, cloisonner les index par utilisateur/tenant, filtrer les documents selon les droits avant récupération, contrôler leur provenance et tester les fuites inter-utilisateurs. |
| **LLM09 — Misinformation** | Les conseils textuels sont des gabarits serveur associés à un type autorisé; les montants sont vérifiés et les réponses malformées produisent un résultat explicitement indisponible sans économies annoncées. | Présenter les réponses comme des suggestions, jamais comme un conseil financier garanti. Continuer à évaluer les sorties et à fournir une réponse neutre sans conseil en cas d’incertitude. |
| **LLM10 — Unbounded Consumption** | L’appel IA est déclenché par une route API; les limites de débit, de taille de requête et de budget ne sont pas visibles dans le code examiné. | Appliquer une limitation de débit par utilisateur, plafonner taille/nombre des catégories et longueur des textes, configurer délais et limites de tokens, quotas/coûts fournisseur et circuit breaker. Mesurer refus, erreurs, latence et consommation sans journaliser les prompts sensibles. |

### 3.3 Ordre d’exécution recommandé

1. **Avant toute intégration réelle** : retirer les valeurs secrètes par défaut en production, restreindre Actuator, valider précisément les JWT et ajouter des tests d’accès croisé entre utilisateurs.
2. **Avant d’autoriser l’analyse IA** : appliquer le consentement, réduire les données envoyées, limiter les entrées et le débit, configurer les quotas/temps d’appel et rendre le mode de repli visible dans les métriques.
3. **Avant toute opération financière réelle** : connecter le fournisseur SCA, ajouter les limites anti-bruteforce et vérifier l’idempotence/concurrence dans PostgreSQL.
4. **Dans la CI** : exécuter tests d’autorisation, validation DTO, tests de sécurité IA (prompt injection, sortie malformée, divulgation, coût), analyse de dépendances et scan d’image conteneur.
5. **Avant production** : faire une revue de menace et un test de pénétration autorisé, traiter les constats, documenter la réponse aux incidents et définir les seuils de surveillance.

## 4. Ce qu’il reste à faire

Les éléments ci-dessous sont des étapes recommandées avant une intégration complète ou une mise en production. Leur priorité est fondée sur le code actuellement présent.

### Priorité 1 — compléter les intégrations et contrôles métier

1. **Connecter le vrai fournisseur SCA/OTP.**  
   Le code d’intention renvoie actuellement un défi statique et la confirmation accepte un code dès lors qu’il contient six caractères. Ce n’est pas une vérification d’identité. Intégrer le fournisseur retenu (OTP, FIDO2 ou autre), vérifier le code côté serveur, limiter les tentatives, rendre le défi à usage unique et couvrir expiration et rejeu par des tests.

2. **Faire respecter le consentement avant l’analyse IA.**  
   Les routes de consentement existent, mais le chemin `/analyze-budget` ne vérifie pas actuellement que l’utilisateur a consenti avant d’appeler le modèle. Définir le comportement attendu en l’absence de consentement ou après révocation, l’appliquer dans le service métier et tester les deux cas.

3. **Finaliser la politique d’export et d’effacement.**  
   L’export actuel contient le profil, les coffres, le consentement et les recommandations. L’effacement supprime les recommandations, les coffres et le profil, puis révoque le consentement ; les données d’audit sont conservées et les intentions/écritures du ledger ne sont pas incluses dans ces opérations. Définir avec les responsables métier et conformité les données à exporter, les durées légales de conservation et les règles de suppression/anonymisation avant de modifier ce comportement.

4. **Rendre l’appel au service de transactions opérationnel.**  
   `TransactionServiceClient` possède un chemin d’API, mais son URL est une valeur par défaut (`https://transaction-service.internal:8443`) et n’est pas définie dans le `docker-compose.yml`. Renseigner `SERVICES_TRANSACTION_SERVICE_BASE_URL` pour l’environnement, vérifier le contrat JSON et le mode de transmission du JWT avec l’équipe du service de transactions, puis tester les erreurs réseau, délais et réponses invalides.

5. **Persister les nouvelles recommandations.**  
   La route d’historique lit la table des recommandations, mais l’analyse IA actuelle retourne son résultat sans créer d’entrée dans cette table. Enregistrer les réponses validées selon la politique de rétention, définir si les réponses de repli sont historisées, puis tester que l’historique restitue les recommandations du bon utilisateur.

### Priorité 2 — démontrer la fiabilité du comportement

6. **Élargir les tests automatisés.**  
   Le dépôt contient un test de chargement Spring et un test unitaire du service IA. Ajouter notamment :
   - des tests unitaires pour les ratios et arrondis, la détection de récurrence, les règles de coffre et le consentement ;
   - des tests d’intégration de chaque API avec authentification, validation, erreurs et isolation entre utilisateurs ;
   - des tests PostgreSQL/Flyway couvrant la création du schéma et les migrations ;
   - des tests de verrouillage couvrant code invalide, expiration, double confirmation, idempotence et concurrence ;
   - des tests des réponses et erreurs du service de transactions et du fournisseur IA.

7. **Préciser la gestion des erreurs.**  
   Le `GlobalExceptionHandler` traite actuellement les erreurs de validation. Définir et documenter les réponses HTTP pour les ressources absentes, les opérations métier refusées, les erreurs des services externes et les erreurs inattendues, sans exposer de détails internes.

8. **Publier le contrat d’API.**  
   Ajouter une spécification OpenAPI maintenue avec les endpoints, DTO, champs obligatoires, réponses d’erreur et authentification JWT. Utiliser cette spécification pour faciliter les tests et l’intégration frontend.

### Priorité 3 — préparer les environnements de livraison

9. **Séparer la configuration locale et production.**  
   Les valeurs de développement de Compose (mot de passe PostgreSQL par défaut et clé IA de remplacement) ne sont pas des secrets de production. Fournir un `.env.example` sans secret, documenter les variables obligatoires et injecter les valeurs réelles via le gestionnaire de secrets de la plateforme.

10. **Ajouter une validation continue (CI).**  
   Créer un pipeline qui exécute `mvnw.cmd test`/Maven sur chaque changement, construit le JAR et l’image Docker, puis publie les résultats. Ajouter les contrôles de dépendances et d’image adaptés au dépôt.

11. **Définir l’exploitation.**  
    Vérifier les probes de santé, métriques, journaux sans données sensibles, alertes, limites de ressources, TLS, sauvegardes/restaurations PostgreSQL et procédure de mise à jour/retour arrière des migrations. Ne pas exposer les détails de santé en accès public sans décision explicite.

## 5. Démarrer en local sous Windows

### Prérequis

- JDK 21 si l’application est lancée directement avec Maven.
- Docker Desktop et Docker Compose si l’application et PostgreSQL sont lancés en conteneurs.
- Une URI JWKS valide pour pouvoir appeler les routes protégées avec un JWT réel.
- Une clé API Groq valide (`GROQ_API_KEY`) pour utiliser le modèle `openai/gpt-oss-120b`. L’identifiant `openai/...` est le nom du modèle, pas le fournisseur OpenAI; l’application utilise l’API compatible OpenAI de Groq.

### Option A — lancer l’application et PostgreSQL avec Docker Compose

Dans PowerShell, depuis la racine du dépôt, créer le fichier de configuration local à partir du modèle, puis l’éditer si nécessaire :

```powershell
Copy-Item .env.example .env
docker compose up --build
```

`.env.example` ne contient que des valeurs fictives destinées au développement local; ne les utilisez jamais en production. Adapter `JWKS_URI` à l’adresse réellement accessible depuis le conteneur. `JWT_ISSUER` doit correspondre exactement au claim `iss` émis par le fournisseur d’identité et `JWT_AUDIENCE` à une valeur du claim `aud` destinée à cette API. Compose publie le service sur `http://localhost:8082` par défaut. Les valeurs d’exemple ne permettent pas une authentification réelle. Le fichier `.env` est ignoré par Git.

### Déploiement avec le profil de production

Le profil `prod` est configuré dans `application-prod.yaml`; il ne contient aucune valeur de repli. Le validateur refuse le démarrage si une valeur critique manque, si les identifiants PostgreSQL par défaut ou les marqueurs de développement sont utilisés, si la base pointe vers `localhost`, ou si JWKS, issuer ou service de transactions n’utilisent pas HTTPS.

Le fichier `docker-compose.prod.yml` complète Compose avec les variables obligatoires et force `SPRING_PROFILES_ACTIVE=prod`. Fournir un fichier `.env.production` **non versionné**, alimenté depuis le gestionnaire de secrets approuvé, puis lancer :

```powershell
docker compose --env-file .env.production -f docker-compose.yml -f docker-compose.prod.yml config --quiet
docker compose --env-file .env.production -f docker-compose.yml -f docker-compose.prod.yml up --build
```

Le fichier doit définir `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `SPRING_DATASOURCE_URL`, `GROQ_API_KEY`, `JWKS_URI`, `JWT_ISSUER`, `JWT_AUDIENCE` et `SERVICES_TRANSACTION_SERVICE_BASE_URL`. `AI_BASE_URL` peut rester à `https://api.groq.com/openai`; `AI_MODEL` peut rester à `openai/gpt-oss-120b` ou être défini vers un autre modèle disponible sur le compte Groq. Les URLs d’identité, du fournisseur IA et du service de transactions doivent être en HTTPS. Ne pas mettre de secret réel dans Git ou dans le fichier d’exemple. Ce Compose fournit le câblage de configuration; le déploiement doit également protéger le réseau, le stockage PostgreSQL et l’accès HTTP derrière l’ingress/TLS de la plateforme.

### Consentement et traitement par l’IA

Avant `POST /api/v1/coach/analyze-budget`, l’utilisateur doit avoir activé le consentement IA via `PUT /api/v1/coach/me/consent`. En l’absence de consentement actif ou après révocation, l’API refuse l’analyse avec HTTP 403 et n’appelle pas le modèle. Le prompt contient uniquement les catégories normalisées et les montants agrégés transmis; le revenu du profil et l’objectif financier libre ne sont pas envoyés. Avant la production, l’équipe doit confirmer auprès du fournisseur les régions de traitement, la rétention, l’usage des données et les conditions contractuelles.

Pour suivre les journaux :

```powershell
docker compose logs -f financial-coach-service
```

Pour arrêter les conteneurs sans supprimer le volume PostgreSQL :

```powershell
docker compose down
```

### Option B — lancer l’application avec Maven

Démarrer d’abord une instance PostgreSQL correspondant aux paramètres `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` et `SPRING_DATASOURCE_PASSWORD`, puis définir les valeurs requises et lancer :

```powershell
$env:SPRING_DATASOURCE_URL = "jdbc:postgresql://localhost:5432/coach_db"
$env:SPRING_DATASOURCE_USERNAME = "postgres"
$env:SPRING_DATASOURCE_PASSWORD = "mot-de-passe-local"
$env:JWKS_URI = "http://localhost:8081/.well-known/jwks.json"
$env:JWT_ISSUER = "valeur-iss-fournie-par-identity-service"
$env:JWT_AUDIENCE = "valeur-aud-configuree-pour-ce-service"
$env:AI_BASE_URL = "https://api.groq.com/openai"
$env:AI_MODEL = "openai/gpt-oss-120b"
$env:GROQ_API_KEY = "votre-cle-groq-locale"
.\mvnw.cmd spring-boot:run
```

Les migrations Flyway sont activées au démarrage. L’application écoute sur le port `8082`.

### Vérifier le service

Depuis PowerShell :

```powershell
Invoke-RestMethod http://localhost:8082/actuator/health
```

Cette requête vérifie l’état exposé par Actuator ; elle ne valide pas à elle seule l’authentification JWT, l’accès à l’IA ou l’intégration au service de transactions.

## 6. Parcours de développement conseillé

1. **Vérifier le point de départ** : installer Java 21, vérifier Docker, puis lancer `.\mvnw.cmd test`.
2. **Démarrer PostgreSQL et le service** : utiliser Docker Compose ou une base locale et confirmer que Flyway applique les migrations `V1` à `V4`.
3. **Configurer l’identité** : fournir une URI JWKS accessible depuis l’application et un JWT de test dont le rôle correspond à `USER`. Vérifier les règles de rôle avec des tests, pas uniquement avec un appel manuel.
4. **Tester les fonctionnalités internes** : commencer par le profil et l’allocation, puis les coffres, les recommandations et les opérations de confidentialité.
5. **Connecter les dépendances externes** : renseigner le fournisseur IA et l’URL du service de transactions, puis valider les contrats et scénarios d’échec.
6. **Fermer les écarts métier prioritaires** : remplacer le défi SCA fictif, appliquer le contrôle de consentement à l’IA et convenir de la politique complète d’export/effacement.
7. **Ajouter les tests de bout en bout** : couvrir les parcours autorisés, refusés et les erreurs transitoires des dépendances.
8. **Automatiser la livraison** : pipeline de tests et de build, configuration par environnement, secrets externes et procédures d’exploitation.

## 7. Commandes de build et de test

Exécuter les tests :

```powershell
.\mvnw.cmd test
```

Construire le JAR :

```powershell
.\mvnw.cmd clean package
```

Le `Dockerfile` exécute actuellement `mvn -B -DskipTests package` pendant la construction de l’image. Il est donc recommandé d’exécuter explicitement les tests avant `docker compose up --build` ; la construction Docker seule ne prouve pas que les tests passent.

## 8. Configuration connue à surveiller

- `server.port` : `8082`.
- Profil local : les replis de développement sont conservés dans `application.yaml` et Compose. `.env.example` est un modèle fictif réservé au développement.
- Profil production : `application-prod.yaml` exige les variables DB, IA, JWKS, issuer, audience, service de transactions et fournisseur SCA. `ProductionConfigurationValidator` rejette les valeurs de développement ciblées et exige HTTPS pour les URLs sensibles.
- JWT : `JWKS_URI` a une valeur locale par défaut sur le port `8081`. Les endpoints Actuator (`/actuator/**`) requièrent un JWT portant le rôle `ADMIN`; les routes métier requièrent le rôle `USER`.
- Identité utilisateur : les endpoints métier utilisent le `sub` du JWT comme UUID propriétaire; un claim `userId` contradictoire n’est pas utilisé.
- Validation JWT : `JWT_ISSUER` et `JWT_AUDIENCE` sont obligatoires. L’issuer doit correspondre exactement au claim `iss`; l’audience configurée doit être présente dans le claim `aud`. Le service valide également la signature et les dates du jeton.
- IA : Spring AI utilise le client compatible OpenAI configuré sur Groq, avec `AI_BASE_URL=https://api.groq.com/openai`, `AI_MODEL=openai/gpt-oss-120b` et la clé `GROQ_API_KEY`. L’identifiant `openai/gpt-oss-120b` désigne le modèle hébergé par Groq; ce n’est pas un modèle xAI Grok. Pour utiliser réellement xAI Grok, il faudra choisir un identifiant `grok-*` et configurer l’URL et la clé xAI correspondantes.
- Limites HTTP : le corps des requêtes est limité à 65 536 octets (`MAX_HTTP_REQUEST_SIZE`). L’analyse IA est limitée à 5 appels/minute/utilisateur et la détection de salaire à 10 (`AI_ANALYSES_PER_MINUTE`, `SALARY_DETECTIONS_PER_MINUTE`). Les limites de débit en mémoire s’appliquent par instance; une installation horizontale doit les partager via la passerelle ou un stockage distribué.
- Bornes métier : revenu mensuel <= 1 000 000 MAD; dépenses IA <= 9 catégories de <= 1 000 000 MAD chacune; objectif d’épargne <= 10 000 000 MAD; verrouillage <= 50 000 MAD. Les montants acceptent au plus deux décimales.
- Pagination recommandations : `page` de 0 à 10 000 et `size` de 1 à 50.
- Service de transactions : propriété Spring `services.transaction-service.base-url`, à fournir notamment via `SERVICES_TRANSACTION_SERVICE_BASE_URL` dans les environnements qui l’utilisent.
- Fournisseur SCA : `SCA_PROVIDER_BASE_URL` et `SCA_PROVIDER_API_TOKEN` sont obligatoires en production. L’adaptateur OTP utilise le contrat décrit dans `README3.md`; en local il est désactivé et les opérations de verrouillage échouent sans fournisseur configuré.
- Ledger : PostgreSQL est requis pour le verrou consultatif par clé d’idempotence; toute réutilisation avec une autre intention ou un autre coffre est un conflit HTTP 409.
- `spring.jpa.hibernate.ddl-auto` vaut `validate` : le schéma doit correspondre aux migrations Flyway, plutôt que d’être créé/modifié automatiquement par Hibernate.

## 9. Résumé

Le dépôt contient déjà le squelette fonctionnel et plusieurs parcours métier importants, ainsi que les migrations et un lancement Docker local. Les prochaines étapes les plus importantes sont de remplacer les parties simulées par les intégrations réelles, de confirmer les conditions de traitement des données du fournisseur IA, puis d’ajouter des tests d’intégration et des contrôles de livraison. Le service doit donc être considéré comme un socle de développement à finaliser, et non comme un service bancaire prêt pour la production.
