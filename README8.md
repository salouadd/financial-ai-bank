# README8 — Informations à confirmer avant le déploiement Swarm

Ce document est un questionnaire de préparation destiné aux équipes **Identity** et **Financial**. Obtenez et consignez leurs réponses **avant de modifier le fichier de stack Swarm**. Les Dockerfiles expliquent comment construire une image, mais `docker stack deploy` ne construit pas les images : il faut publier les images au registre au préalable.

> **Règle de sécurité :** ne mettez jamais de mot de passe de registre, de clé privée, de token ou d’autre secret dans le fichier YAML, ce README ou Git. Utilisez le mécanisme de secrets approuvé par l’organisation.

## 1. Questions à poser aux deux équipes

Remplissez une fiche par service (Identity et Financial) et par dépendance qu’il faut déployer.

### Image et registre

- **Nom complet de l’image, registre compris :**
  `____________________________________________________________`
  - Exemple : `registry.example.com/equipe/identity:1.2.3`
- **Tag immuable ou digest de l’image à déployer :**
  `____________________________________________________________`
  - Évitez `latest` pour un déploiement contrôlé. Préférez un tag de version immuable ou une référence par digest.
- **Confirmation que l’image a été construite et poussée :**
  `Oui / Non — par : __________ — date/version : __________`
- **Confirmation que tous les nœuds Swarm qui peuvent exécuter le service peuvent télécharger cette image :**
  `Oui / Non — précisions : __________________________________`
- **Registre privé ?** `Oui / Non`
  - Si oui, comment l’authentification est-elle fournie au déploiement et renouvelée ?
    `____________________________________________________________`
  - La procédure doit éviter d’inscrire des identifiants dans le YAML ou dans Git.

### Contrat réseau et application

- **Port d’écoute réel dans le conteneur :** `________`
- **Le serveur écoute sur `0.0.0.0` / toutes les interfaces du conteneur :** `Oui / Non — preuve : __________`
  - Une écoute limitée à `127.0.0.1` ne permet pas aux autres conteneurs de joindre le service.
- **Adresse et chemin de santé (liveness), méthode et réponse attendue :**
  `____________________________________________________________`
- **Adresse et chemin de disponibilité (readiness), méthode et conditions de réussite :**
  `____________________________________________________________`
  - Précisez les dépendances vérifiées et ce qui indique que le service peut réellement traiter des requêtes.
- **Routes de l’API à intégrer :** pour chacune, fournir chemin exact, méthode HTTP, authentification, corps JSON, réponse JSON et codes d’erreur.
  - Login : `___________________________________________________`
  - JWKS (Identity) : `_________________________________________`
  - Comptes (création, lecture, autres opérations requises) : `________`
- **Variables d’environnement effectivement reconnues** (nom, rôle, valeur requise ou défaut) :
  `____________________________________________________________`
- **Délai de démarrage et temps maximal avant disponibilité :**
  `____________________________________________________________`
- **Ressources recommandées** (CPU et mémoire, demande et limite) :
  `____________________________________________________________`
- **Politique de redémarrage recommandée et comportement en cas d’échec :**
  `____________________________________________________________`

### Dépendances et données

- Le service dépend-il d’une base de données, d’un broker, d’un stockage de fichiers ou d’autres services ? Indiquez chaque dépendance :
  `____________________________________________________________`
- Pour chacune, précisez l’image/version, le port **interne**, le réseau Swarm et le nom DNS/service à utiliser :
  `____________________________________________________________`
- Quelles variables de connexion sont requises ? Comment sont-elles fournies sans les exposer ?
  `____________________________________________________________`
- Des migrations de schéma sont-elles exécutées au démarrage ou séparément ? Quelle est la procédure et comment éviter les incompatibilités pendant un déploiement progressif ?
  `____________________________________________________________`
- Quelle procédure de sauvegarde et de restauration faut-il appliquer avant une mise à jour ?
  `____________________________________________________________`
- Quelles données doivent survivre au redémarrage ou au remplacement d’une tâche ? Indiquez les volumes, leur emplacement et les contraintes de stockage partagé entre nœuds :
  `____________________________________________________________`
- Quels secrets sont requis (mots de passe, clés, tokens) ? Quel mécanisme Swarm approuvé le service sait-il lire (secret monté en fichier, variable injectée par un gestionnaire, autre) ?
  `____________________________________________________________`

Ne supposez pas que les applications sont autonomes. Une tâche peut démarrer alors que ses bases de données ou services associés sont absents ou injoignables, et rester inutilisable. Confirmez la connectivité depuis le réseau Swarm et testez les dépendances avant de déclarer le service prêt.

## 2. Contrat JWT Identity / Gateway

Demandez à l’équipe Identity et faites valider par l’équipe Gateway les points suivants :

- [ ] Les JWT sont signés en **RS256**.
- [ ] La valeur réelle du claim `iss` est fournie. Elle est `identity-service` ou `JWT_ISSUER` est configuré avec la valeur exacte effectivement émise.
- [ ] Le JWKS public est accessible depuis le réseau Swarm à `http://<nom-du-service-identity>:<port-interne>/.well-known/jwks.json` (ou à l’URL interne convenue).
- [ ] Les JWT contiennent les claims `sub` et `role`.
- [ ] Les valeurs exactes de `role` sont confirmées et compatibles avec les contrôles du Gateway, notamment `USER` et `ADMIN` si ce sont les valeurs attendues. Confirmez aussi leur représentation exacte (par exemple `USER` ou `ROLE_USER`) : ne présumez pas qu’elles sont interchangeables.
- [ ] Les valeurs attendues pour `aud`, l’expiration et les autres validations du Gateway sont documentées.
- [ ] Les nœuds et services concernés peuvent joindre le JWKS, y compris lors de la rotation des clés.

**La Gateway ne reçoit que les clés publiques via JWKS. Ne distribuez jamais à la Gateway la clé privée de signature.**

## 3. Éléments déjà repérés dans ce dépôt Financial Coach

Ces éléments proviennent de la configuration de ce dépôt et doivent être confirmés par l’équipe avant de les considérer comme le contrat de production.

| Élément | Information repérée | À confirmer |
|---|---|---|
| Port applicatif | `8082` (`server.port` dans `application.yaml`) | Confirmer le port d’écoute effectif et l’écoute sur toutes les interfaces. |
| Image de l’application | Aucune image Swarm publiée/référencée dans `docker-compose.yml` ; le fichier utilise `build:` | Fournir l’image versionnée ou son digest, construite et poussée. |
| Base de données | PostgreSQL, port interne `5432`; Compose local utilise `postgres:17-alpine` | Confirmer l’image/version approuvée pour la production, la connectivité et le réseau Swarm. |
| Persistance de PostgreSQL | Volume Compose local `postgres_data` vers `/var/lib/postgresql/data` | Définir le stockage persistant de production, les sauvegardes et la restauration. |
| Migrations | Flyway est activé dans `application.yaml` | Confirmer la stratégie de migration et le plan de retour arrière. |
| JWT | `JWKS_URI`, `JWT_ISSUER`, `JWT_AUDIENCE` sont configurés | Fournir les valeurs de production et valider le contrat avec Identity/Gateway. |
| Dépendances externes | Le profil de production exige notamment une URL de Transaction Service et une URL/token de fournisseur SCA; une API IA Groq est configurée | Confirmer quelles dépendances sont activées, leurs accès réseau, leurs secrets et leurs contrôles de disponibilité. |
| Santé et ressources | Aucun healthcheck Swarm ni limite CPU/mémoire n’est défini dans les fichiers Compose présents | Fournir les chemins/conditions de santé et les valeurs de ressources recommandées. |
| Redémarrage local | `restart: unless-stopped` dans `docker-compose.yml` | Confirmer la politique de redémarrage adaptée au déploiement Swarm. |

Les routes Identity actuellement documentées à titre de repère dans `README7.md` sont `POST /users/login`, `GET /users/me`, `POST /users/register` et `GET /.well-known/jwks.json`. L’équipe Identity doit confirmer les chemins, méthodes, schémas JSON, codes de réponse et l’accessibilité depuis Swarm avant intégration.

## 4. Conditions préalables à l’édition de la stack

`docker-stack.yml` est pour l’instant un modèle non déployable : l’image Financial Coach n’est pas encore publiée, la base PostgreSQL est laissée externe (son contrat de production reste à confirmer), et les réponses des équipes, secrets Swarm et réseau externe restent à confirmer. Le secret `financial_coach_runtime_config` attendu par la stack doit être un fichier YAML Spring contenant notamment le mot de passe DB, `spring.ai.openai.api-key` et `services.sca.api-token`. Sa présence ne signifie pas que les prérequis ci-dessous sont satisfaits.

Ne modifiez le fichier de stack que lorsque les deux équipes ont fourni les informations nécessaires et que les points bloquants sont résolus :

- [ ] Images immuables publiées; téléchargement vérifié depuis les nœuds Swarm concernés.
- [ ] Registre privé et mécanisme d’authentification au déploiement documentés, sans secret dans le YAML.
- [ ] Ports internes, interfaces d’écoute, réseaux et noms DNS des services confirmés.
- [ ] Routes de santé et critères de disponibilité validés.
- [ ] Routes Identity (login, JWKS, comptes) et formats JSON confirmés.
- [ ] Contrat JWT, valeurs `iss`/`aud`/`role` et accès au JWKS validés avec Gateway.
- [ ] Bases de données et autres dépendances accessibles sur les réseaux prévus.
- [ ] Secrets, migrations, persistance, sauvegarde et restauration documentés.
- [ ] Délais de démarrage, ressources et politique de redémarrage acceptés.

Lors du déploiement, la stack doit référencer des images déjà construites et publiées; `docker stack deploy` ne prend pas en charge la construction via `build:` comme `docker compose up --build`. Pour un registre privé, utilisez la procédure d’authentification approuvée par l’organisation au moment du déploiement, sans écrire les identifiants dans un fichier de stack.
