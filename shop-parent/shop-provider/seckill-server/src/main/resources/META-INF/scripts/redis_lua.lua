local stringKey=KEYS[1]
local stringVal=ARGV[1]
local expireAt=tonumber(ARGV[2])
local keyExist=redis.call("SETNX",KEYS[1],stringVal)
if(keyExist>=1) then
    redis.call("EXPIRE",KEYS[1],expireAt)
    return true
end
return false
