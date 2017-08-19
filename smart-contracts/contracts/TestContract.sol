pragma solidity ^0.4.0;

import "./TestContract.sol";

contract TestContract {
    uint256 public value;

    function TestContract(
        uint256 _value)
    {
        value = _value;
    }

    function getValue() constant returns (uint256) {
        return value;
    }
}
