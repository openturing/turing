import React from 'react';
import PropTypes from 'prop-types';

const VigBullet = ({ value = 'test', color='black'}) => {
  var bulletStyle = {
    position: 'relative',
    top: '1px',
    display: 'inline-block',
    width: '12px',
    height: '12px',
    borderRadius: '50%',
    backgroundColor: color,
    marginTop: "12px"
  }
  return (
    <div>
      <span style={bulletStyle}>
      </span>
      <span style={{marginLeft: '6px'}}>
        {value}
      </span>
    </div>
  );
};

VigBullet.propTypes = {
  value: PropTypes.string.isRequired,
  color: PropTypes.string.isRequired
};
export default VigBullet;