package com.cameraunlock.zomboid;

import se.krka.kahlua.luaj.compiler.LuaCompiler;
import zombie.GameTime;
import zombie.Lua.LuaManager;
import zombie.characters.IsoPlayer;
import zombie.input.GameKeyboard;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoWorld;
import zombie.ui.UIManager;

/**
 * The test scenes of a development build, for stepping through everything a player does before a
 * release: Numpad + goes to the next and Numpad - to the one before, round and round. Each takes
 * the first player to a place that has what it tests, waits for the map there to load, clears the
 * zombies round it, holds the hour and a clear sky, makes the player invincible, finds the thing
 * tested and puts it in a known state, hands over and wields what the test needs, stands the player
 * where the thing is in the middle of the view, and puts the view in first person or the game's
 * own as the scene says. A scene that finds nothing to test says so on the screen. What to do in
 * each and what to look for is in .lab/GAMEPLAY_DEFECTS/RUNBOOK.md.
 */
final class DevScenes {
    private static final int NEXT_KEY = 78, LAST_KEY = 74;
    /** Frames the map round a scene's place is given after its square is loaded, for the rooms and objects round it. */
    private static final int SETTLE_FRAMES = 45;
    private static final long LOAD_WITHIN_NANOS = 15_000_000_000L;
    /** The eye height the look is aimed from, in tiles above the floor. */
    private static final float EYE = 1.6f;

    /** Where the scenes are set: tiles that are the same in every world, since the map is. */
    private static final float STREET_X = 13502.5f, STREET_Y = 2553.5f;
    private static final float WOOD_X = 10900.5f, WOOD_Y = 8500.5f;
    private static final float FARM_X = 9050.5f, FARM_Y = 8700.5f;
    private static final float TOWER_X = 12772.5f, TOWER_Y = 1565.5f;

    /**
     * A scene: what the screen calls it, the one thing to do, whether it is played in first person,
     * the hour it is held at, where the player is first taken, and the Lua that sets it up. The Lua
     * stands the player and returns the point to look at (x, y and height above the floor).
     */
    private record Scene(String title, String doing, boolean firstPerson, float hour, float x, float y, String lua) {}

    private static final String DOOR = """
            local d,sq=DS.near(function(o) return instanceof(o,"IsoDoor") and not o:isBarricaded() end, 30)
            if not d then error("no door within 30 tiles") end
            d:setLocked(false); d:setLockedByKey(false)
            if d:IsOpen() then d:ToggleDoor(getPlayer()) end
            return DS.faceEdge(d, sq, 1.4, 1.1)
            """;
    private static final String WINDOW = """
            local w,sq=DS.near(function(o) return instanceof(o,"IsoWindow") and not o:isSmashed() and not o:isBarricaded() end, 30)
            if not w then error("no whole window within 30 tiles") end
            w:setIsLocked(false)
            if w:IsOpen() then w:ToggleWindow(getPlayer()) end
            return DS.faceEdge(w, sq, 1.0, 1.3)
            """;

    private static final Scene[] SCENES = {
            new Scene("Door", "E opens it, E again closes it", true, 13f, STREET_X, STREET_Y, DOOR),
            new Scene("Window", "E opens it, E on the open window climbs through", true, 13f, STREET_X, STREET_Y, WINDOW),
            new Scene("Light switch", "E switches the room's light", true, 20f, STREET_X, STREET_Y, """
                    local s,sq=DS.near(function(o,q) return instanceof(o,"IsoLightSwitch") and q:getRoom()~=nil end, 30)
                    if not s then error("no light switch in a room within 30 tiles") end
                    return DS.faceWall(s, sq, 0.85, 1.3)
                    """),
            new Scene("Curtains", "Stood indoors: E opens and closes them", true, 13f, STREET_X, STREET_Y, """
                    local c,sq=DS.near(function(o,q)
                      if not instanceof(o,"IsoCurtain") then return false end
                      return pcall(DS.faceEdge, o, q, 0.6, 1.3)
                    end, 30)
                    if not c then error("no curtains with free floor on their room side within 30 tiles") end
                    return DS.faceEdge(c, sq, 0.6, 1.3)
                    """),
            new Scene("Fridge", "E opens its loot, double-click the apple to take it", true, 13f, STREET_X, STREET_Y, """
                    local f,sq=DS.near(function(o) return o:getContainerByType("fridge")~=nil end, 30)
                    if not f then error("no fridge within 30 tiles") end
                    f:getContainerByType("fridge"):AddItem("Base.Apple")
                    return DS.faceThing(sq, 1.0)
                    """),
            new Scene("Kitchen counter", "E opens its loot, drag the knife from it to your inventory", true, 13f, STREET_X, STREET_Y, """
                    local c,sq=DS.near(function(o,q) return o:getContainerByType("counter")~=nil and not DS.has(q,"Sink") end, 30)
                    if not c then error("no kitchen counter within 30 tiles") end
                    c:getContainerByType("counter"):AddItem("Base.KitchenKnife")
                    return DS.faceThing(sq, 0.8)
                    """),
            new Scene("Sink", "Pointer out, right-click the sink, Drink or Wash (E opens the cupboard under it)", true, 13f, STREET_X, STREET_Y, """
                    local s,sq=DS.near(function(o) return string.find(DS.cn(o),"Sink")~=nil end, 30)
                    if not s then error("no sink within 30 tiles") end
                    return DS.faceThing(sq, 0.9)
                    """),
            new Scene("Chair", "Pointer out, right-click the chair, Sit", true, 13f, STREET_X, STREET_Y, """
                    local c,sq=DS.near(function(o) return string.find(DS.cn(o),"Chair")~=nil end, 30)
                    if not c then error("no chair within 30 tiles") end
                    return DS.faceThing(sq, 0.5)
                    """),
            new Scene("Bed", "Pointer out, right-click the bed, Sleep: the clock runs on and you wake", true, 21f, STREET_X, STREET_Y, """
                    local b,sq=DS.near(function(o) return string.find(DS.cn(o),"Bed")~=nil end, 30)
                    if not b then error("no bed within 30 tiles") end
                    getPlayer():getStats():set(CharacterStat.FATIGUE, 0.8)
                    return DS.faceThing(sq, 0.3)
                    """),
            new Scene("Things on the floor", "E on each to pick it up", true, 13f, STREET_X, STREET_Y, """
                    DS.bare()
                    local x,y=DS.p():getX(),DS.p():getY()
                    local sq=getCell():getGridSquare(math.floor(x)+1,math.floor(y),0)
                    DS.drop(sq,"Base.Hammer",0.3,0.4); DS.drop(sq,"Base.WaterBottle",0.7,0.6); DS.drop(sq,"Base.Apple",0.5,0.2)
                    DS.stand(x-0.3,y,0,sq:getX()+0.5,sq:getY()+0.5)
                    return sq:getX()+0.5, sq:getY()+0.5, 0
                    """),
            new Scene("Drop and place", "Drop the mug from your inventory, then Place it from its menu where you look", true, 13f, STREET_X, STREET_Y, """
                    DS.bare(); DS.give("Base.Mugl")
                    local x,y=DS.p():getX(),DS.p():getY()
                    return DS.ahead(1.5, 0)
                    """),
            new Scene("Walk To", "Press your Walk To key and click a spot ahead", true, 13f, STREET_X, STREET_Y, """
                    DS.bare()
                    return DS.ahead(4, 0)
                    """),
            new Scene("Move furniture", "Pointer out, right-click the chair, Pick Up, then place it again", true, 13f, STREET_X, STREET_Y, """
                    local c,sq=DS.near(function(o) return string.find(DS.cn(o),"Chair")~=nil end, 30)
                    if not c then error("no chair within 30 tiles") end
                    return DS.faceThing(sq, 0.5)
                    """),
            new Scene("Disassemble", "Pointer out, right-click the chair, Disassemble", true, 13f, STREET_X, STREET_Y, """
                    DS.give("Base.Screwdriver"); DS.give("Base.Hammer"); DS.give("Base.Saw")
                    local c,sq=DS.near(function(o) return string.find(DS.cn(o),"Chair")~=nil end, 30)
                    if not c then error("no chair within 30 tiles") end
                    return DS.faceThing(sq, 0.5)
                    """),
            new Scene("Barricade", "Pointer out, right-click the window, Barricade", true, 13f, STREET_X, STREET_Y, """
                    DS.wield("Base.Hammer"); DS.give("Base.Plank",4); DS.give("Base.Nails",12)
                    """ + WINDOW),
            new Scene("Eat, drink, read", "In your inventory, right-click the crisps (Eat), the water (Drink), the magazine (Read)", true, 13f, STREET_X, STREET_Y, """
                    DS.bare(); DS.give("Base.Crisps"); DS.give("Base.WaterBottle"); DS.give("Base.Magazine")
                    local s=getPlayer():getStats(); s:set(CharacterStat.HUNGER, 0.4); s:set(CharacterStat.THIRST, 0.4)
                    return DS.ahead(3, 1.2)
                    """),
            new Scene("Stairs", "Walk up the stairs and back down", true, 13f, TOWER_X, TOWER_Y, """
                    local sq=DS.nearsq(function(q) return q:HasStairs() end, 60)
                    if not sq then error("no stairs within 60 tiles") end
                    return DS.foot(sq)
                    """),
            new Scene("Fence", "Climb over the fence as you would in the game's own view", true, 17.5f, STREET_X, STREET_Y, """
                    local f,sq=DS.near(function(o) return DS.hoppable(o) and string.find(o:getSprite():getName() or "","fenc") end, 30)
                    if not f then error("no fence to climb within 30 tiles") end
                    return DS.faceEdge(f, sq, 0.7, 0.9)
                    """),
            new Scene("Melee", "Left-click to swing the bat at the zombie", true, 17.5f, STREET_X, STREET_Y, """
                    DS.street(); DS.wield("Base.BaseballBat")
                    local lx,ly=DS.ahead(1, 0)
                    DS.zombie(lx,ly)
                    return lx,ly,1.3
                    """),
            new Scene("Shove", "Shove the zombie with your melee key (Space)", true, 17.5f, STREET_X, STREET_Y, """
                    DS.street(); DS.bare()
                    local lx,ly=DS.ahead(0.8, 0)
                    DS.zombie(lx,ly)
                    return lx,ly,1.3
                    """),
            new Scene("Pistol", "Hold right mouse to aim, left-click to fire, R to reload", true, 17.5f, STREET_X, STREET_Y, """
                    DS.street(); DS.wield("Base.Pistol")
                    local lx,ly=DS.ahead(6, 0)
                    DS.zombie(lx,ly)
                    return lx,ly,1.3
                    """),
            new Scene("Shotgun crowd", "Aim and fire round the ring: limbs and heads come off", true, 18.5f, STREET_X, STREET_Y, """
                    DS.street(); DS.wield("Base.Shotgun")
                    local p=DS.p(); local x,y=p:getX(),p:getY()
                    for i=0,7 do local a=i*math.pi/4; DS.zombie(x+math.cos(a)*2.4, y+math.sin(a)*2.4) end
                    local lx,ly=DS.ahead(2.4, 0)
                    return lx,ly,1.3
                    """),
            new Scene("Scoped rifle", "Aim down the street through the scope and fire", true, 17.5f, STREET_X, STREET_Y, """
                    DS.street()
                    local r=DS.wield("Base.HuntingRifle"); r:attachWeaponPart(instanceItem("Base.x4Scope"))
                    local lx,ly=DS.ahead(22, 0)
                    DS.zombie(lx,ly)
                    return lx,ly,1.3
                    """),
            new Scene("Get in a car", "E at the driver's door gets you in", true, 17.5f, STREET_X, STREET_Y, """
                    DS.street(); DS.bare()
                    local v=DS.car(0, -5)
                    local a=v:getAreaCenter("SeatFrontLeft")
                    DS.stand(a:getX(),a:getY(),0,v:getX(),v:getY())
                    return v:getX(),v:getY(),1.0
                    """),
            new Scene("Drive", "Start the engine with your engine key (N) and drive with W, A, S, D; E gets you out", true, 17.5f, STREET_X, STREET_Y, """
                    DS.street(); DS.bare()
                    local v=DS.car(0, -5)
                    v:setHotwired(true)
                    v:enter(0, DS.p())
                    return v:getX(), v:getY()-6, 1.0
                    """),
            new Scene("Build in the game's own view", "Build, then click a green spot: the crate is built", false, 13f, STREET_X, STREET_Y, """
                    DS.street(); DS.builder()
                    return DS.ahead(2, 0)
                    """),
            new Scene("Build in first person", "Build, then place the crate in front of you", true, 13f, STREET_X, STREET_Y, """
                    DS.street(); DS.builder()
                    return DS.ahead(2, 0)
                    """),
            new Scene("Chop a tree", "Pointer out, right-click the tree, Chop Down Tree", true, 13f, WOOD_X, WOOD_Y, """
                    DS.wield("Base.Axe")
                    local t,sq=DS.near(function(o) return instanceof(o,"IsoTree") end, 30)
                    if not t then error("no tree within 30 tiles") end
                    return DS.faceThing(sq, 2.0)
                    """),
            new Scene("Dig a furrow", "Pointer out, right-click the ground ahead, Dig Furrow, then plant the seeds", true, 13f, FARM_X, FARM_Y, """
                    DS.wield("Base.HandShovel"); DS.give("Base.CarrotBagSeed2")
                    return DS.ahead(2, 0)
                    """),
            new Scene("Panels", "Open and close the inventory, health, skills, info, map and crafting panels", true, 13f, STREET_X, STREET_Y, """
                    DS.street(); DS.bare()
                    return DS.ahead(4, 1.2)
                    """),
            new Scene("Switch views", "Start eating the crisps, then press Delete twice while you eat", true, 13f, STREET_X, STREET_Y, """
                    DS.street(); DS.bare(); DS.give("Base.Crisps")
                    getPlayer():getStats():set(CharacterStat.HUNGER, 0.4)
                    return DS.ahead(4, 1.2)
                    """),
            new Scene("Save and quit", "Esc, Quit: back at the main menu, Continue loads you here again", true, 17.5f, STREET_X, STREET_Y, """
                    DS.street(); DS.bare()
                    return DS.ahead(6, 1.4)
                    """),
    };

    /**
     * The Lua the scenes share, put in the game's Lua once a session as the table DS. Each helper
     * stands the player itself where it frames a thing, and returns the point to look at.
     */
    private static final String HELPERS = """
            DS={items={},given={},cars={}}
            function DS.p() return getPlayer() end
            function DS.cn(o) local pr=o:getProperties(); return pr and pr:get("CustomName") or "" end
            function DS.near(pred, r)
              local p=getPlayer(); local px,py,pz=math.floor(p:getX()),math.floor(p:getY()),math.floor(p:getZ())
              local best,bsq,bd
              for dx=-r,r do for dy=-r,r do
                local sq=getCell():getGridSquare(px+dx,py+dy,pz)
                if sq then local os=sq:getObjects(); for i=0,os:size()-1 do local o=os:get(i)
                  if pred(o,sq) then local d=dx*dx+dy*dy; if not bd or d<bd then best,bsq,bd=o,sq,d end end
                end end
              end end
              return best,bsq
            end
            function DS.nearsq(pred, r)
              local p=getPlayer(); local px,py,pz=math.floor(p:getX()),math.floor(p:getY()),math.floor(p:getZ())
              local best,bd
              for dx=-r,r do for dy=-r,r do
                local sq=getCell():getGridSquare(px+dx,py+dy,pz)
                if sq and pred(sq) then local d=dx*dx+dy*dy; if not bd or d<bd then best,bd=sq,d end end
              end end
              return best
            end
            function DS.has(sq,name) local os=sq:getObjects(); for i=0,os:size()-1 do if string.find(DS.cn(os:get(i)),name) then return true end end; return false end
            function DS.roomAt(x,y,z) local sq=getCell():getGridSquare(math.floor(x),math.floor(y),z); return sq~=nil and sq:getRoom()~=nil end
            function DS.free(x,y,z) local sq=getCell():getGridSquare(math.floor(x),math.floor(y),z); return sq~=nil and sq:isFree(false) end
            function DS.stand(x,y,z,lx,ly)
              local p=getPlayer(); p:teleportTo(x,y,z); p:setX(x); p:setY(y); p:setLastX(x); p:setLastY(y)
              local dx,dy=lx-x,ly-y; local l=math.sqrt(dx*dx+dy*dy)
              if l>0 then p:setForwardDirection(dx/l,dy/l) end
            end
            function DS.faceEdge(o, sq, back, h)
              local z=sq:getZ(); local ex,ey,a,b
              local north
              if instanceof(o,"IsoDoor") or instanceof(o,"IsoWindow") or instanceof(o,"IsoCurtain") then north=o:getNorth() else north=o:getProperties():has(IsoFlagType.HoppableN) end
              if north then ex,ey=sq:getX()+0.5,sq:getY(); a,b={ex,ey+back},{ex,ey-back}
              else ex,ey=sq:getX(),sq:getY()+0.5; a,b={ex+back,ey},{ex-back,ey} end
              local s
              if instanceof(o,"IsoCurtain") then
                s=DS.roomAt(a[1],a[2],z) and a or (DS.roomAt(b[1],b[2],z) and b or nil)
                if s and not DS.free(s[1],s[2],z) then s=nil end
              else s=DS.free(a[1],a[2],z) and a or (DS.free(b[1],b[2],z) and b or nil) end
              if not s then error("no free floor on either side of it at "..sq:getX()..","..sq:getY()) end
              DS.stand(s[1],s[2],z,ex,ey)
              return ex,ey,h
            end
            function DS.faceWall(o, sq, back, h)
              local pr=o:getProperties(); local z=sq:getZ(); local x,y=sq:getX(),sq:getY()
              if pr:has(IsoFlagType.attachedN) then DS.stand(x+0.5,y+back,z,x+0.5,y); return x+0.5,y,h end
              if pr:has(IsoFlagType.attachedW) then DS.stand(x+back,y+0.5,z,x,y+0.5); return x,y+0.5,h end
              return DS.faceThing(sq, h)
            end
            function DS.faceThing(sq, h)
              local z=sq:getZ()
              for _,d in ipairs({{1,0},{-1,0},{0,1},{0,-1},{1,1},{-1,1},{1,-1},{-1,-1}}) do
                local s=getCell():getGridSquare(sq:getX()+d[1],sq:getY()+d[2],z)
                if s and s:isFree(false) and not sq:isBlockedTo(s) and not s:isBlockedTo(sq) then
                  DS.stand(s:getX()+0.5+d[1]*0.3,s:getY()+0.5+d[2]*0.3,z,sq:getX()+0.5,sq:getY()+0.5)
                  return sq:getX()+0.5,sq:getY()+0.5,h
                end
              end
              error("no free floor beside it at "..sq:getX()..","..sq:getY())
            end
            function DS.ahead(d, h)
              local p=getPlayer(); local f=p:getForwardDirection()
              return p:getX()+f:getX()*d, p:getY()+f:getY()*d, h
            end
            function DS.foot(sq)
              local z=sq:getZ(); local x,y=sq:getX(),sq:getY()
              if sq:HasStairsNorth() then
                while getCell():getGridSquare(x,y+1,z) and getCell():getGridSquare(x,y+1,z):HasStairsNorth() do y=y+1 end
                if not DS.free(x+0.5,y+1.8,z) then error("no floor at the foot of the stairs at "..x..","..y) end
                DS.stand(x+0.5,y+1.8,z,x+0.5,y-1); return x+0.5,y-1,2.4
              end
              while getCell():getGridSquare(x+1,y,z) and getCell():getGridSquare(x+1,y,z):HasStairsWest() do x=x+1 end
              if not DS.free(x+1.8,y+0.5,z) then error("no floor at the foot of the stairs at "..x..","..y) end
              DS.stand(x+1.8,y+0.5,z,x-1,y+0.5); return x-1,y+0.5,2.4
            end
            function DS.hoppable(o)
              if not o:getProperties() or instanceof(o,"IsoWindow") or instanceof(o,"IsoDoor") then return false end
              local ok,low=pcall(function() return o:isHoppable() end)
              return ok and low
            end
            function DS.street() DS.stand(%1$s,%2$s,0,%1$s,%2$s-10) end
            function DS.bare() local p=getPlayer(); p:setPrimaryHandItem(nil); p:setSecondaryHandItem(nil) end
            function DS.give(t,n) local inv=getPlayer():getInventory(); local it; for i=1,(n or 1) do it=inv:AddItem(t); DS.given[#DS.given+1]=it end; return it end
            function DS.drop(sq,t,x,y) DS.items[#DS.items+1]=sq:AddWorldInventoryItem(t,x,y,0) end
            function DS.tidy()
              local p=getPlayer(); local inv=p:getInventory()
              for _,it in ipairs(DS.given) do
                if p:getPrimaryHandItem()==it then p:setPrimaryHandItem(nil) end
                if p:getSecondaryHandItem()==it then p:setSecondaryHandItem(nil) end
                if it:getContainer()==inv then inv:DoRemoveItem(it) end
              end
              for _,it in ipairs(DS.items) do
                local w=it:getWorldItem()
                if w and w:getSquare() then w:getSquare():transmitRemoveItemFromSquare(w) end
              end
              for _,v in ipairs(DS.cars) do v:permanentlyRemove() end
              DS.given,DS.items,DS.cars={},{},{}
              local ui=UIManager.getUI()
              for i=ui:size()-1,0,-1 do local t=ui:get(i):getTable()
                if t and t.Type=="ISBuildWindow" then t:close(); t:removeFromUIManager() end
                if t and t.Type=="ISModalDialog" then t:destroy() end
              end
            end
            function DS.wield(t)
              local p=getPlayer(); local it=DS.give(t)
              p:setPrimaryHandItem(it)
              if it:isTwoHandWeapon() or (instanceof(it,"HandWeapon") and it:isAimedFirearm()) then p:setSecondaryHandItem(it) else p:setSecondaryHandItem(nil) end
              return it
            end
            function DS.zombie(x,y)
              local p=getPlayer()
              addZombiesInOutfit(math.floor(x),math.floor(y),math.floor(p:getZ()),1,nil,50)
              local l=getCell():getZombieList(); local z=l:get(l:size()-1)
              z:setX(x); z:setY(y); z:setLastX(x); z:setLastY(y); z:setUseless(true); z:faceThisObject(p)
              return z
            end
            function DS.car(dx, dy)
              local p=getPlayer()
              local sq=getCell():getGridSquare(math.floor(p:getX())+dx, math.floor(p:getY())+dy, 0)
              local v=addVehicleDebug("Base.CarNormal", IsoDirections.N, nil, sq)
              if not v then error("no car could be put at "..sq:getX()..","..sq:getY()) end
              v:repair()
              local g=v:getPartById("GasTank"); if g then g:setContainerContentAmount(g:getContainerCapacity()) end
              DS.cars[#DS.cars+1]=v
              DS.given[#DS.given+1]=p:getInventory():AddItem(v:createVehicleKey())
              return v
            end
            function DS.builder()
              local p=getPlayer(); local inv=p:getInventory()
              DS.wield("Base.Hammer"); DS.give("Base.Plank",16); DS.give("Base.Nails",40)
              p:setPerkLevelDebug(Perks.Woodwork, 6)
              ISEntityUI.OpenBuildWindow(p, nil, "*")
              local w=ISBuildWindow.instance; local all=w.logic:getAllBuildableRecipes()
              for i=0,all:size()-1 do local r=all:get(i)
                if r:getName()=="Wood_Crate_Lvl1" then w.logic:setRecipe(r); w:onRecipeChanged(r); return end
              end
              error("the Building window has no Wooden Crate (Basic)")
            end
            """.formatted(STREET_X, STREET_Y);

    /** The scene last chosen, and none before the first press. */
    private static int scene = -1;
    private static boolean helpersIn;
    /** A scene waiting for the map round its place, since when, and how many frames it has been loaded. */
    private static Scene waiting;
    private static long waitingSince;
    private static int settled;
    /** Where a scene's first person view is turned to once first person is on, or NaN. */
    private static float lookYaw = Float.NaN, lookPitch;

    private DevScenes() {}

    /** Logic thread, once a frame from DevKeys: says what a key or a scene did, for the screen, or null. */
    static String poll(IsoPlayer player) {
        boolean back = GameKeyboard.isKeyPressed(LAST_KEY);
        if (back || GameKeyboard.isKeyPressed(NEXT_KEY)) {
            scene = back ? Math.floorMod(Math.max(scene, 0) - 1, SCENES.length) : (scene + 1) % SCENES.length;
            waiting = SCENES[scene];
            waitingSince = System.nanoTime();
            settled = 0;
            lookYaw = Float.NaN;
            if (player.getVehicle() != null) {
                player.getVehicle().exit(player);
            }
            player.teleportTo(waiting.x(), waiting.y(), 0);
            Log.info("scene " + label() + ": taken to (" + waiting.x() + ", " + waiting.y() + "), waiting for the map there");
            return label() + ": getting ready";
        }
        if (waiting != null) {
            return ready(player);
        }
        FirstPerson firstPerson = View.first().firstPerson;
        if (!Float.isNaN(lookYaw) && firstPerson.active()) {
            firstPerson.setLook(lookYaw, lookPitch);
            lookYaw = Float.NaN;
        }
        return null;
    }

    private static String label() {
        return "Test " + (scene + 1) + "/" + SCENES.length + " " + SCENES[scene].title();
    }

    private static String ready(IsoPlayer player) {
        IsoGridSquare square = IsoWorld.instance.currentCell.getGridSquare((int) Math.floor(waiting.x()), (int) Math.floor(waiting.y()), 0);
        if (square == null || !square.getChunk().loaded) {
            if (System.nanoTime() - waitingSince > LOAD_WITHIN_NANOS) {
                Log.info("scene " + label() + ": the map round (" + waiting.x() + ", " + waiting.y() + ") did not load in " + LOAD_WITHIN_NANOS / 1_000_000_000L + " s");
                waiting = null;
                return label() + ": the map there did not load";
            }
            return null;
        }
        if (++settled < SETTLE_FRAMES) {
            return null;
        }
        Scene now = waiting;
        waiting = null;
        DevKeys.invincible = true;
        DevKeys.cleared = false;
        DevKeys.clearSky(true);
        GameTime.getInstance().setTimeOfDay(now.hour());
        Object[] ran = lua("""
                local p=getPlayer()
                local ui=UIManager.getUI(); for i=0,ui:size()-1 do local t=ui:get(i):getTable(); if t and t.Type=="ISContextMenu" then t:setVisible(false) end end
                getCell():setDrag(nil, 0)
                DS.tidy()
                local l=getCell():getZombieList()
                for i=l:size()-1,0,-1 do local z=l:get(i)
                  if math.abs(z:getX()-p:getX())<40 and math.abs(z:getY()-p:getY())<40 then z:removeFromWorld(); z:removeFromSquare() end
                end
                """ + now.lua());
        if (!Boolean.TRUE.equals(ran[0])) {
            String why = ran.length > 1 ? String.valueOf(ran[1]) : "no reason given";
            Log.info("scene " + label() + " could not be set up: " + why);
            return label() + ": " + why;
        }
        if (ran.length >= 4 && ran[1] instanceof Double lx && ran[2] instanceof Double ly && ran[3] instanceof Double lh) {
            double dx = lx - player.getX(), dy = ly - player.getY();
            lookYaw = (float) Math.atan2(dy, dx);
            lookPitch = (float) Math.atan2(lh - EYE, Math.max(Math.hypot(dx, dy), 0.3));
        }
        if (now.firstPerson() != Quality.firstPersonInEffect(0)) {
            View.first().firstPerson.toggle();
        }
        Log.info("scene " + label() + " (" + (now.firstPerson() ? "first person" : "the game's own view") + ", " + now.hour() + ":00) at ("
                + player.getX() + ", " + player.getY() + ", " + player.getZ() + "): " + now.doing());
        return label() + ": " + now.doing();
    }

    private static Object[] lua(String chunk) {
        try {
            if (!helpersIn) {
                Object[] ran = LuaManager.caller.pcall(UIManager.getDefaultThread(), LuaCompiler.loadstring(HELPERS, "DevScenes helpers", LuaManager.env), new Object[0]);
                if (!Boolean.TRUE.equals(ran[0])) {
                    throw new IllegalStateException("the scenes' Lua helpers do not run: " + (ran.length > 1 ? ran[1] : "no reason given"));
                }
                helpersIn = true;
            }
            return LuaManager.caller.pcall(UIManager.getDefaultThread(), LuaCompiler.loadstring(chunk, "DevScenes " + label(), LuaManager.env), new Object[0]);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("a scene's Lua does not compile: " + e, e);
        }
    }
}
