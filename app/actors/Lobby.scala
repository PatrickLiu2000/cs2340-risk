package actors

import actors.LobbyState.State
import akka.actor.{Actor, ActorRef, Props}
import common.{Resources, UniqueIdProvider, UniqueValueManager, Util}
import controllers._
import models.{Client, ClientSettings}
import play.api.Logger

import scala.collection.immutable.HashSet
import scala.collection.mutable

object Lobby extends UniqueIdProvider {
  // Methods for UniqueIdProvider
  override def idLength = 4
  private val IdCharsSet: Set[Char] = HashSet() ++ Resources.LobbyIdChars
  override protected def generateId(len: Int): String =
    Util.randomString(len, Resources.LobbyIdChars)
  override protected def isIdChar(c: Char): Boolean = IdCharsSet.contains(c)

  // Actor factory methods
  def props: Props = Props[Lobby]
  def apply(id: String, hostInfo: ClientSettings): Props =
    Props({
      val l = new Lobby(id)
      l.initialHostSettings = Some(hostInfo)
      l
    })
}

/**
  * Lobby actor that supervises a collection of connected clients that
  * may or may not have actually joined
  * @param id The unique lobby Id
  */
class Lobby(val id: String)
    extends Actor with UniqueValueManager[ClientSettings] {

  val logger: Logger = Logger(this.getClass)

  // *************
  // Mutable state
  // *************

  // The ClientSettings of the initial host (submitted through the form
  // on the landing page)
  var initialHostSettings: Option[ClientSettings] = None
  // List of clients who have joined the lobby
  val players: mutable.LinkedHashMap[String, ClientWithActor] =
    mutable.LinkedHashMap[String, ClientWithActor]()
  // List of clients who have established a ws connection, but not in lobby)
  val connected: mutable.LinkedHashMap[String, ClientWithActor] =
    mutable.LinkedHashMap[String, ClientWithActor]()
  // Always a member of players
  var host: Option[ClientWithActor] = None
  // Current state of the lobby
  var state: State = LobbyState.Lobby

  def hasInitialHostJoined: Boolean = initialHostSettings.isEmpty
  def hasHost: Boolean = host.isEmpty

  def startGame(): Unit = {
    // TODO Implement
  }

  // Receive incoming packets and turn them into internal state
  // changes and/or outgoing packets (returns partial function)
  override def receive: Receive = {
    case _: LobbyPacket => receiveLobby
    case _: InGamePacket => receiveInGame
  }

  // Handle incoming packets in the lobby state
  def receiveLobby: Receive = {
    case ClientConnect(_: String, clientId: String, actor: ActorRef) =>
      if (!hasInitialHostJoined) {
        // Add the initial host to the list of players
        val client = ClientWithActor(Client(clientId, initialHostSettings), actor)
        players += clientId -> client
        host = Some(client)
        initialHostSettings = None
        notifyLobbyChanged()
      } else {
        connected += clientId -> ClientWithActor(Client(clientId), actor)
        // Send current lobby information
        actor ! constructLobbyUpdate(generateLobbyInfo)
      }

    case RequestClientJoin(_, clientId: String, withSettings: ClientSettings) =>
      if (connected.isDefinedAt(clientId)) {
        if (!ClientSettings.isValid(withSettings))
          // Reject with response
          connected(clientId).actor ! RequestReply(RequestResponse.Rejected,
            ClientSettings.formatInvalid(withSettings))
        else if (!isUnique(withSettings))
          // Reject with response
          connected(clientId).actor ! RequestReply(RequestResponse.Rejected,
            "Name and color must be unique: non-unique inputs " +
              s"{${nonUniqueElements(withSettings).mkString(", ")}}")
        else {
          val client = connected(clientId)
          connected -= clientId
          players += clientId -> client
          // Approve with response
          client.actor ! RequestReply(RequestResponse.Accepted)
          // Broadcast lobby update to all other players
          notifyLobbyChanged(client.actor)
        }
      }

    // Disconnect within Lobby stage
    case ClientDisconnect(_, clientId: String) =>
      if (connected.isDefinedAt(clientId))
      // Client hadn't actually joined, silently remove them
        connected -= clientId
      else if (host.exists(_.client.id == clientId)) {
        // Host disconnecting
        players.remove(clientId)
        // Promote the first-joined player to host if there is one
        if (players.isEmpty) host = None
        else host = Some(players.head._2)
        notifyLobbyChanged()
      } else {
        // Normal player disconnecting
        players.remove(clientId)
        notifyLobbyChanged()
      }

    case RequestStartLobby(_, clientId: String) =>
      if (host.exists(_.client.id == clientId)) {
        if (players.size >= Resources.MinimumPlayers) {
          // Request is coming from the host, start game
          (players.values.iterator ++ connected.values.iterator)
            .foreach(_.actor ! StartLobby)
          connected.empty
          this.state = LobbyState.InGame
          startGame()

        } else
        // Reject with response
          connected.getOrElse(clientId, players(clientId)).actor !
            RequestReply(RequestResponse.Rejected, "Cannot start game: " +
              s"not enough players (min: ${Resources.MinimumPlayers})")

      } else
      // Reject with response
        connected.getOrElse(clientId, players(clientId)).actor !
          RequestReply(RequestResponse.Rejected, "Must be the host " +
            "of the lobby to start it (invalid privileges)")

    case p: InGamePacket =>
      // Packet sent for wrong state
      badPacket(p)

    case p: InPacket =>
      // Bad/unknown InPacket
      badPacket(p)

    case _ =>
  }

  // Handle incoming packets during the InGame state
  def receiveInGame: Receive = {
    case p: InGamePacket =>
      // Packet sent for wrong state
      badPacket(p)

    case p: InPacket =>
      // Bad/unknown InPacket
      badPacket(p)

    case _ =>
  }

  /**
    * Sends a message to all players connected to the lobby (connected
    * AND players)
    * @param exclude Optional client ActorRef to exclude sending the
    *                message to (used when accepting RequestClientJoins)
    */
  def notifyLobbyChanged(exclude: ActorRef = null): Unit = {
    val lobby = generateLobbyInfo
    (players.valuesIterator ++ connected.valuesIterator)
      .filter(_.actor != exclude)
      .foreach(_.actor ! constructLobbyUpdate(lobby))
  }

  def generateLobbyInfo: Iterable[ClientSettings] = players.valuesIterator
    .map(_.client.settings)
    .filter(_.isDefined)
    .map(_.get).toList

  def constructLobbyUpdate(lobby: Iterable[ClientSettings]) =
    LobbyUpdate(lobby, host
      .map(_.client.settings.map(_.name).getOrElse(""))
      .getOrElse(""))

  def packetInvalidState(p: InPacket): Unit = {
    logger.debug(s"Packet received $p is invalid for current state $state")
    connected.get(p.clientId).orElse(players.get(p.clientId)).foreach(
      actor => actor.actor ! BadPacket(s"Bad/unknown InPacket received: $p")
    )
  }

  def badPacket(p: InPacket): Unit = {
    logger.debug(s"Bad/unknown InPacket received: $p")
    connected.get(p.clientId).orElse(players.get(p.clientId)).foreach(
      actor => actor.actor ! BadPacket(s"Bad/unknown InPacket received: $p")
    )
  }
}

// State enumeration for a lobby
object LobbyState extends Enumeration {
  type State = Value
  val Lobby, InGame = Value
}