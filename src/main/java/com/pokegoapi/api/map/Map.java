/*
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.pokegoapi.api.map;

import POGOProtos.Map.Fort.FortDataOuterClass;
import POGOProtos.Map.Fort.FortTypeOuterClass;
import POGOProtos.Map.MapCellOuterClass;
import POGOProtos.Map.Pokemon.MapPokemonOuterClass;
import POGOProtos.Map.Pokemon.NearbyPokemonOuterClass;
import POGOProtos.Map.Pokemon.WildPokemonOuterClass;
import POGOProtos.Map.SpawnPointOuterClass;
import POGOProtos.Networking.Requests.Messages.*;
import POGOProtos.Networking.Requests.RequestTypeOuterClass;
import POGOProtos.Networking.Responses.*;
import com.google.protobuf.InvalidProtocolBufferException;
import com.pokegoapi.api.PokemonGo;
import com.pokegoapi.api.map.Pokemon.CatchablePokemon;
import com.pokegoapi.api.map.Pokemon.NearbyPokemon;
import com.pokegoapi.api.map.fort.FortDetails;
import com.pokegoapi.exceptions.LoginFailedException;
import com.pokegoapi.exceptions.RemoteServerException;
import com.pokegoapi.google.common.geometry.MutableInteger;
import com.pokegoapi.google.common.geometry.S2CellId;
import com.pokegoapi.google.common.geometry.S2LatLng;
import com.pokegoapi.main.ServerRequest;
import java8.util.function.Function;
import java8.util.stream.Collectors;
import java8.util.stream.StreamSupport;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

import static com.pokegoapi.google.common.geometry.S2CellId.MAX_LEVEL;

public class Map {

	private final PokemonGo api;
	private MapObjects cachedMapObjects;
	@Getter
	@Setter
	private boolean useCache;

	@Setter
	@Getter
	private long mapObjectsExpiry;

	private long lastMapUpdate;

	public Map(PokemonGo api) {
		this.api = api;
		cachedMapObjects = new MapObjects(api);
		lastMapUpdate = 0;
		useCache = true;
	}

	public void clearCache(){
		this.lastMapUpdate = 0;
		this.cachedMapObjects = new MapObjects(api);
	}

	/**
	 * Returns a list of catchable pokemon around the current location
	 *
	 * @return List<CatchablePokemon> at your current location
	 */
	public List<CatchablePokemon> getCatchablePokemon() throws LoginFailedException, RemoteServerException {
		List<CatchablePokemon> catchablePokemons = new ArrayList<>();
		MapObjects objects = getMapObjects();

		for(MapPokemonOuterClass.MapPokemon mapPokemon : objects.getCatchablePokemons()){
			catchablePokemons.add(new CatchablePokemon(api, mapPokemon));
		}

		for(WildPokemonOuterClass.WildPokemon wildPokemon : objects.getWildPokemons()){
			catchablePokemons.add(new CatchablePokemon(api, wildPokemon));
		}

		return catchablePokemons;
	}

	/**
	 * Returns a list of nearby pokemon (non-catchable)
	 *
	 * @return List<NearbyPokemon> at your current location
	 */
	public List<NearbyPokemon> getNearbyPokemon() throws LoginFailedException, RemoteServerException {
		List<NearbyPokemon> pokemons = new ArrayList<>();
		MapObjects objects = getMapObjects();

		for (NearbyPokemonOuterClass.NearbyPokemon pokemon : objects.getNearbyPokemons()) {
			pokemons.add(new NearbyPokemon(pokemon));
		}

		return pokemons;
	}

	/**
	 * Returns a list of spawn points
	 *
	 * @return List<Point> list of spawn points
	 */
	public List<Point> getSpawnPoints() throws LoginFailedException, RemoteServerException {
		List<Point> points = new ArrayList<>();
		MapObjects objects = getMapObjects();

		for (SpawnPointOuterClass.SpawnPoint point : objects.getSpawnPoints()) {
			points.add(new Point(point));
		}

		return points;
	}

	/**
	 * Returns a list of decimated spawn points at current location
	 *
	 * @return List<Point> list of spawn points
	 */
	public List<Point> getDecimatedSpawnPoints() throws LoginFailedException, RemoteServerException {
		List<Point> points = new ArrayList<>();
		MapObjects objects = getMapObjects();

		for (SpawnPointOuterClass.SpawnPoint point : objects.getDecimatedSpawnPoints()) {
			points.add(new Point(point));
		}

		return points;
	}

	/**
	 * Returns MapObjects around your current location
	 *
	 * @return MapObjects at your current location
	 */
	public MapObjects getMapObjects() throws LoginFailedException, RemoteServerException {
		return getMapObjects(9);
	}

	/**
	 * Returns MapObjects around your current location within a given width
	 *
	 * @param width
	 * @return MapObjects at your current location
	 */
	public MapObjects getMapObjects(int width) throws LoginFailedException, RemoteServerException {
		return getMapObjects(getCellIds(api.getLatitude(), api.getLongitude(), width), api.getLatitude(), api.getLongitude(), api.getAltitude());
	}

	/**
	 * Returns 9x9 cells with the requested lattitude/longitude in the center cell
	 *
	 * @param latitude
	 * @param longitude
	 * @return MapObjects in the given cells
	 */
	@Deprecated
	public MapObjects getMapObjects(double latitude, double longitude) throws LoginFailedException, RemoteServerException {
		return getMapObjects(latitude, longitude, 9);
	}

	/**
	 * Returns the cells requested, you should send a latitude/longitude to fake a near location
	 *
	 * @param cellIds   List<Long> of cellId
	 * @param latitude
	 * @param longitude
	 * @return MapObjects in the given cells
	 */
	@Deprecated
	public MapObjects getMapObjects(List<Long> cellIds, double latitude, double longitude) throws LoginFailedException, RemoteServerException {
		return getMapObjects(cellIds, latitude, longitude, 0);
	}

	/**
	 * Returns `width` * `width` cells with the requested latitude/longitude in the center
	 *
	 * @param latitude
	 * @param longitude
	 * @param width
	 * @return MapObjects in the given cells
	 */
	@Deprecated
	public MapObjects getMapObjects(double latitude, double longitude, int width) throws LoginFailedException, RemoteServerException {
		return getMapObjects(getCellIds(latitude, longitude, width), latitude, longitude);
	}

	/**
	 * Returns the cells requested
	 *
	 * @param cellIds
	 * @param latitude
	 * @param longitude
	 * @return MapObjects in the given cells
	 */
	@Deprecated
	public MapObjects getMapObjects(List<Long> cellIds, double latitude, double longitude, double altitude) throws LoginFailedException, RemoteServerException {
		api.setLatitude(latitude);
		api.setLongitude(longitude);
		api.setAltitude(altitude);
		return getMapObjects(cellIds);
	}

	/**
	 * Returns the cells requested
	 *
	 * @param cellIds List<Long> of cellId
	 * @return MapObjects in the given cells
	 */
	public MapObjects getMapObjects(List<Long> cellIds) throws LoginFailedException, RemoteServerException {

		if(useCache && (System.currentTimeMillis() - lastMapUpdate > mapObjectsExpiry)){
			lastMapUpdate = 0;
			cachedMapObjects = new MapObjects(api);
		}

		GetMapObjectsMessageOuterClass.GetMapObjectsMessage.Builder builder = GetMapObjectsMessageOuterClass.GetMapObjectsMessage.newBuilder()
				.setLatitude(api.getLatitude())
				.setLongitude(api.getLongitude());

		for (Long cellId : cellIds) {
			builder.addCellId(cellId);
			builder.addSinceTimestampMs(lastMapUpdate);

		}

		ServerRequest serverRequest = new ServerRequest(RequestTypeOuterClass.RequestType.GET_MAP_OBJECTS, builder.build());
		api.getRequestHandler().request(serverRequest);
		api.getRequestHandler().sendServerRequests();
		GetMapObjectsResponseOuterClass.GetMapObjectsResponse response = null;
		try {
			response = GetMapObjectsResponseOuterClass.GetMapObjectsResponse.parseFrom(serverRequest.getData());
		} catch (InvalidProtocolBufferException e) {
			throw new RemoteServerException(e);
		}

		MapObjects result = new MapObjects(api);
		for (MapCellOuterClass.MapCell mapCell : response.getMapCellsList()) {
			result.addNearbyPokemons(mapCell.getNearbyPokemonsList());
			result.addCatchablePokemons(mapCell.getCatchablePokemonsList());
			result.addWildPokemons(mapCell.getWildPokemonsList());
			result.addDecimatedSpawnPoints(mapCell.getDecimatedSpawnPointsList());
			result.addSpawnPoints(mapCell.getSpawnPointsList());

			java.util.Map<FortTypeOuterClass.FortType, List<FortDataOuterClass.FortData>> groupedForts
					= StreamSupport.stream(mapCell.getFortsList()).collect(Collectors.groupingBy(new Function<FortDataOuterClass.FortData, FortTypeOuterClass.FortType>() {
				@Override
				public FortTypeOuterClass.FortType apply(FortDataOuterClass.FortData t) {
					return t.getType();
				}
			}));
			result.addGyms(groupedForts.get(FortTypeOuterClass.FortType.GYM));
			result.addPokestops(groupedForts.get(FortTypeOuterClass.FortType.CHECKPOINT));
		}

		if(useCache){
			cachedMapObjects.update(result);
			result = cachedMapObjects;
			lastMapUpdate = System.currentTimeMillis();
		}

		return result;
	}

	/**
	 * Get a list of all the Cell Ids
	 *
	 * @param latitude
	 * @param longitude
	 * @param width
	 * @return List of Cells
	 */
	public List<Long> getCellIds(double latitude, double longitude, int width) {
		S2LatLng latLng = S2LatLng.fromDegrees(latitude, longitude);
		S2CellId cellId = S2CellId.fromLatLng(latLng).parent(15);

		MutableInteger i = new MutableInteger(0);
		MutableInteger j = new MutableInteger(0);

		int level = cellId.level();
		int size = 1 << (MAX_LEVEL - level);
		int face = cellId.toFaceIJOrientation(i, j, null);

		List<Long> cells = new ArrayList<Long>();

		int halfWidth = (int) Math.floor(width / 2);
		for (int x = -halfWidth; x <= halfWidth; x++) {
			for (int y = -halfWidth; y <= halfWidth; y++) {
				cells.add(cellId.fromFaceIJ(face, i.intValue() + x * size, j.intValue() + y * size).parent(15).id());
			}
		}
		return cells;
	}

	public FortDetails getFortDetails(String id, double lon, double lat) throws LoginFailedException, RemoteServerException {
		FortDetailsMessageOuterClass.FortDetailsMessage reqMsg = FortDetailsMessageOuterClass.FortDetailsMessage.newBuilder()
				.setFortId(id)
				.setLatitude(lat)
				.setLongitude(lon)
				.build();

		ServerRequest serverRequest = new ServerRequest(RequestTypeOuterClass.RequestType.FORT_DETAILS, reqMsg);
		api.getRequestHandler().request(serverRequest);
		api.getRequestHandler().sendServerRequests();
		FortDetailsResponseOuterClass.FortDetailsResponse response = null;
		try {
			response = FortDetailsResponseOuterClass.FortDetailsResponse.parseFrom(serverRequest.getData());
		} catch (InvalidProtocolBufferException e) {
			throw new RemoteServerException(e);
		}
		return new FortDetails(response);
	}

	@Deprecated
	public FortSearchResponseOuterClass.FortSearchResponse searchFort(FortDataOuterClass.FortData fortData) throws LoginFailedException, RemoteServerException {
		FortSearchMessageOuterClass.FortSearchMessage reqMsg = FortSearchMessageOuterClass.FortSearchMessage.newBuilder()
				.setFortId(fortData.getId())
				.setFortLatitude(fortData.getLatitude())
				.setFortLongitude(fortData.getLongitude())
				.setPlayerLatitude(api.getLatitude())
				.setPlayerLongitude(api.getLongitude())
				.build();
		ServerRequest serverRequest = new ServerRequest(RequestTypeOuterClass.RequestType.FORT_SEARCH, reqMsg);
		api.getRequestHandler().request(serverRequest);
		api.getRequestHandler().sendServerRequests();
		FortSearchResponseOuterClass.FortSearchResponse response = null;
		try {
			response = FortSearchResponseOuterClass.FortSearchResponse.parseFrom(serverRequest.getData());
		} catch (InvalidProtocolBufferException e) {
			throw new RemoteServerException(e);
		}
		return response;
	}

	@Deprecated
	public EncounterResponseOuterClass.EncounterResponse encounterPokemon(MapPokemonOuterClass.MapPokemon catchablePokemon) throws LoginFailedException, RemoteServerException {
		EncounterMessageOuterClass.EncounterMessage reqMsg = EncounterMessageOuterClass.EncounterMessage.newBuilder()
				.setEncounterId(catchablePokemon.getEncounterId())
				.setPlayerLatitude(api.getLatitude())
				.setPlayerLongitude(api.getLongitude())
				.setSpawnpointId(catchablePokemon.getSpawnpointId())
				.build();
		ServerRequest serverRequest = new ServerRequest(RequestTypeOuterClass.RequestType.ENCOUNTER, reqMsg);
		api.getRequestHandler().request(serverRequest);
		api.getRequestHandler().sendServerRequests();
		EncounterResponseOuterClass.EncounterResponse response = null;
		try {
			response = EncounterResponseOuterClass.EncounterResponse.parseFrom(serverRequest.getData());
		} catch (InvalidProtocolBufferException e) {
			throw new RemoteServerException(e);
		}
		return response;
	}

	@Deprecated
	public CatchPokemonResponseOuterClass.CatchPokemonResponse catchPokemon(MapPokemonOuterClass.MapPokemon catchablePokemon, double normalizedHitPosition, double normalizedReticleSize, double spinModifier, int pokeball) throws LoginFailedException, RemoteServerException {
		CatchPokemonMessageOuterClass.CatchPokemonMessage reqMsg = CatchPokemonMessageOuterClass.CatchPokemonMessage.newBuilder()
				.setEncounterId(catchablePokemon.getEncounterId())
				.setHitPokemon(true)
				.setNormalizedHitPosition(normalizedHitPosition)
				.setNormalizedReticleSize(normalizedReticleSize)
				.setSpawnPointGuid(catchablePokemon.getSpawnpointId())
				.setSpinModifier(spinModifier)
				.setPokeball(pokeball)
				.build();
		ServerRequest serverRequest = new ServerRequest(RequestTypeOuterClass.RequestType.CATCH_POKEMON, reqMsg);
		api.getRequestHandler().request(serverRequest);
		api.getRequestHandler().sendServerRequests();
		CatchPokemonResponseOuterClass.CatchPokemonResponse response = null;
		try {
			response = CatchPokemonResponseOuterClass.CatchPokemonResponse.parseFrom(serverRequest.getData());
		} catch (InvalidProtocolBufferException e) {
			throw new RemoteServerException(e);
		}
		return response;
	}
}
